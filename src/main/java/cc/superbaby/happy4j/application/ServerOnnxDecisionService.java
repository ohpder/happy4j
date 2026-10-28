package cc.superbaby.happy4j.application;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import cc.superbaby.happy4j.domain.Card;
import cc.superbaby.happy4j.domain.GameStage;
import cc.superbaby.happy4j.domain.TableSnapshot;

import jakarta.annotation.PreDestroy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the same DouZero FullAuto ResNet models used by the browser on the server.
 *
 * <p>The browser and server must use identical 54-column card features and 40x54
 * candidate tensors; otherwise a server-hosted game would silently use a different
 * policy from a browser-hosted game.</p>
 */
@Slf4j
@Service
public final class ServerOnnxDecisionService implements AutoCloseable {
    private static final String MODEL_ROOT = "static/models/douzero_fullauto/";
    private static final int FEATURE_WIDTH = 54;
    private static final int FEATURE_ROWS = 40;
    private static final int MAX_BATCH = 64;

    private static final Map<String, String> PLAY_MODELS = Map.of(
            "landlord", "landlord.onnx",
            "landlord_up", "landlord_up.onnx",
            "landlord_down", "landlord_down.onnx");

    private static final Map<String, String> PRE_GAME_MODELS = Map.of(
            "bid", "bid.onnx",
            "farmer_up", "farmer_up.onnx",
            "farmer_down", "farmer_down.onnx");

    private static final Map<String, float[]> ROLE_PRIORS = Map.of(
            "landlord", new float[]{1f, .5f, 1f, 1f, 1f, 1f, 1f, 5f, -4f, 1f, 1f, 1f, 1f, .8f, 1.3f},
            "landlord_up", new float[]{1f, .2f, 1f, 1f, 3.5f, 1f, 1f, 5f, 4f, 1.035f, 1f, .15f, 1f, 2.5f, 1.3f},
            "landlord_down", new float[]{1f, .2f, 1f, 1f, 3.5f, 1f, 1f, 5f, 4f, 1.035f, 1f, .15f, 1f, 2.5f, 1.3f});

    private final OrtEnvironment environment;
    private final ServerAiDecisionService local;
    private final ConcurrentHashMap<String, OrtSession> playSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, OrtSession> preGameSessions = new ConcurrentHashMap<>();

    public ServerOnnxDecisionService(ServerAiDecisionService local) {
        environment = OrtEnvironment.getEnvironment();
        this.local = local;
    }

    public boolean chooseLandlord(TableSnapshot table) {
        boolean robbing = table.availableActions().stream().anyMatch(action ->
                action.name().equals("ROB_LANDLORD") || action.name().equals("PASS_ROB"));
        double score = scoreHand("bid", table.hand());
        boolean previouslyActed = table.actionHistory().stream()
                .anyMatch(entry -> entry.seatId() == table.seatId()
                        && (entry.action().contains("叫") || entry.action().contains("抢")));
        double threshold = robbing ? (previouslyActed ? .4 : .5) : .2;
        return score > threshold;
    }

    public ServerAiDecisionService.DoubleDecision chooseDouble(TableSnapshot table, long superDoubleItems) {
        String role = roleFor(table);
        boolean landlord = "landlord".equals(role);
        double score = landlord
                ? initialLandlordScore(table)
                : scoreHand(role.equals("landlord_up") ? "farmer_up" : "farmer_down", table.hand());
        long robbed = table.actionHistory().stream().filter(entry -> "叫/抢地主".equals(entry.action())).count();
        double superThreshold = landlord ? (robbed > 1 ? .6 : .5) : 1.2;
        double doubleThreshold = landlord ? (robbed > 1 ? .5 : .4) : 1.0;
        if (table.availableActions().stream().anyMatch(action -> action.name().equals("SUPER_DOUBLE"))
                && superDoubleItems > 0 && score > superThreshold && hasSuperPattern(table.hand()))
            return new ServerAiDecisionService.DoubleDecision("SUPER_DOUBLE", scoreStrength(score));
        return new ServerAiDecisionService.DoubleDecision(
                table.availableActions().stream().anyMatch(action -> action.name().equals("DOUBLE"))
                        && score > doubleThreshold ? "DOUBLE" : "NO_DOUBLE", scoreStrength(score));
    }

    /**
     * Scores legal moves with the role-specific FullAuto model and returns the best
     * move. A caller may fall back to the deterministic policy if the model cannot be
     * loaded or the public history is incomplete.
     */
    public ServerAiDecisionService.PlayDecision choosePlay(TableSnapshot table,
                                                            List<List<Integer>> candidates) {
        long start = System.currentTimeMillis();
        if (candidates == null || candidates.isEmpty())
            return new ServerAiDecisionService.PlayDecision(List.of(), true, 0,
                    "服务器 ONNX 没有可提交的合法动作");
        if (table.stage() != GameStage.PLAYING)
            throw new IllegalStateException("当前不在出牌阶段");
        if (table.turnSeatId() != table.seatId())
            throw new IllegalStateException("当前不是本机出牌回合");
        if (!stateComplete(table))
            throw new IllegalStateException("公开牌历史不完整，暂不向 ONNX 输入牌局特征");

        String role = roleFor(table);
        OrtSession session = playSessions.computeIfAbsent(role, key -> loadPlaySession(key));
        float[] scores = scoreCandidates(session, table, candidates, role);
        int best = bestIndex(scores);
        List<Integer> cards = List.copyOf(candidates.get(best));
        log.debug("服务器 ONNX 出牌评分: {} {} {} {}ms", role, cards, Arrays.toString(scores), System.currentTimeMillis() - start);
        return new ServerAiDecisionService.PlayDecision(cards, cards.isEmpty(), candidates.size(),
                "DouZero FullAuto ResNet ONNX " + PLAY_MODELS.get(role));
    }

    private float[] scoreCandidates(OrtSession session, TableSnapshot table,
                                    List<List<Integer>> candidates, String role) {
        float[] result = new float[candidates.size()];
        for (int start = 0; start < candidates.size(); start += MAX_BATCH) {
            int end = Math.min(candidates.size(), start + MAX_BATCH);
            List<List<Integer>> batch = candidates.subList(start, end);
            float[] z = encodeZ(table, batch);
            float[] x = encodeX(batch.size(), role);
            try (OnnxTensor zTensor = OnnxTensor.createTensor(environment,
                    FloatBuffer.wrap(z), new long[]{batch.size(), FEATURE_ROWS, FEATURE_WIDTH});
                 OnnxTensor xTensor = OnnxTensor.createTensor(environment,
                         FloatBuffer.wrap(x), new long[]{batch.size(), 15});
                 OrtSession.Result output = run(session, Map.of("z", zTensor, "x", xTensor))) {
                float[] values = flatten(output.get(0));
                if (values.length < batch.size())
                    throw new IllegalStateException("ONNX 输出评分数量不足");
                System.arraycopy(values, 0, result, start, batch.size());
            } catch (OrtException failure) {
                throw new IllegalStateException("服务器 ONNX 出牌推理失败", failure);
            }
        }
        return result;
    }

    private double initialLandlordScore(TableSnapshot table) {
        List<List<Integer>> candidates = local.allMoves(table);
        if (candidates.isEmpty()) return 0;
        String role = "landlord";
        OrtSession session = playSessions.computeIfAbsent(role, key -> loadSession(role, PLAY_MODELS.get(role)));
        float[] scores = scoreCandidates(session, table, candidates, role);
        float best = scores[0];
        for (float score : scores) best = Math.max(best, score);
        return best * 8d;
    }

    private double scoreHand(String model, List<Card> hand) {
        OrtSession session = preGameSessions.computeIfAbsent(model,
                key -> loadSession(key, PRE_GAME_MODELS.get(key)));
        float[] input = cardsToScoreArray(hand);
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input), new long[]{1, 60});
             OrtSession.Result output = run(session, Map.of("cards", tensor))) {
            float[] values = flatten(output.get(0));
            if (values.length == 0) throw new IllegalStateException("ONNX 局前模型没有返回评分");
            return values[0];
        } catch (OrtException failure) {
            throw new IllegalStateException("服务器 ONNX 局前推理失败: " + model, failure);
        }
    }

    private static OrtSession.Result run(OrtSession session, Map<String, OnnxTensor> inputs)
            throws OrtException {
        synchronized (session) {
            return session.run(inputs);
        }
    }

    private OrtSession loadPlaySession(String role) {
        String file = PLAY_MODELS.get(role);
        if (file == null) throw new IllegalArgumentException("未知服务器 ONNX 角色: " + role);
        return loadSession(role, file);
    }

    private OrtSession loadSession(String role, String file) {
        try (InputStream input = new ClassPathResource(MODEL_ROOT + file).getInputStream();
             OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(1);
            options.setInterOpNumThreads(1);
            OrtSession session = environment.createSession(input.readAllBytes(), options);
            log.info("Loaded {} for {}", file, role);
            return session;
        } catch (IOException | OrtException failure) {
            throw new IllegalStateException("加载服务器 ONNX 模型失败: " + file, failure);
        }
    }

    private static float[] cardsToScoreArray(List<Card> cards) {
        float[] output = new float[60];
        Map<Integer, Integer> counts = new HashMap<>();
        for (Card card : cards) counts.merge(strength(card.code()), 1, Integer::sum);
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int rank = entry.getKey();
            int column = rank <= 15 ? rank - 3 : rank - 3;
            if (column < 0 || column >= 15) continue;
            for (int row = 0; row < Math.min(4, entry.getValue()); row++)
                output[row * 15 + column] = 1f;
        }
        return output;
    }

    private static boolean hasSuperPattern(List<Card> hand) {
        int[] counts = new int[15];
        for (Card card : hand) {
            int rank = strength(card.code());
            if (rank >= 3 && rank <= 17) counts[rank - 3]++;
        }
        int ace = counts[11];
        int two = counts[12];
        int small = counts[13];
        int big = counts[14];
        return small > 0 && big > 0 || big > 0 && two > 0 || small > 0 && two >= 2
                || two >= 3 && ace > 0 || two >= 2 && ace >= 3;
    }

    private static int scoreStrength(double score) {
        return (int) Math.round(score * 1000d);
    }

    private static int bestIndex(float[] values) {
        int best = 0;
        for (int index = 1; index < values.length; index++)
            if (Float.compare(values[index], values[best]) > 0) best = index;
        return best;
    }

    private static float[] flatten(OnnxValue value) throws OrtException {
        Object raw = value.getValue();
        if (raw instanceof float[] values) return values;
        if (raw instanceof float[][] values) {
            List<Float> result = new ArrayList<>();
            for (float[] row : values) for (float item : row) result.add(item);
            float[] output = new float[result.size()];
            for (int index = 0; index < result.size(); index++) output[index] = result.get(index);
            return output;
        }
        if (raw instanceof double[] values) {
            float[] result = new float[values.length];
            for (int index = 0; index < values.length; index++) result[index] = (float) values[index];
            return result;
        }
        if (raw instanceof double[][] values) {
            List<Float> result = new ArrayList<>();
            for (double[] row : values) for (double item : row) result.add((float) item);
            float[] output = new float[result.size()];
            for (int index = 0; index < result.size(); index++) output[index] = result.get(index);
            return output;
        }
        throw new IllegalStateException("无法解析 ONNX 输出类型: " + raw.getClass().getName());
    }

    private static float[] encodeZ(TableSnapshot table, List<List<Integer>> candidates) {
        SharedFeatures shared = sharedFeatures(table);
        float[] output = new float[candidates.size() * FEATURE_ROWS * FEATURE_WIDTH];
        for (int batch = 0; batch < candidates.size(); batch++) {
            int offset = batch * FEATURE_ROWS * FEATURE_WIDTH;
            put(output, offset, cardsToArray(candidates.get(batch)));
            offset += FEATURE_WIDTH;
            for (float[] row : shared.rows()) {
                put(output, offset, row);
                offset += FEATURE_WIDTH;
            }
        }
        return output;
    }

    private static float[] encodeX(int batchSize, String role) {
        float[] prior = ROLE_PRIORS.getOrDefault(role, ROLE_PRIORS.get("landlord"));
        float[] output = new float[batchSize * prior.length];
        for (int batch = 0; batch < batchSize; batch++)
            System.arraycopy(prior, 0, output, batch * prior.length, prior.length);
        return output;
    }

    private static void put(float[] target, int offset, float[] source) {
        System.arraycopy(source, 0, target, offset, source.length);
    }

    private static SharedFeatures sharedFeatures(TableSnapshot table) {
        List<TableSnapshot.ActionLog> history = table.actionHistory().stream()
                .filter(entry -> "出牌".equals(entry.action()) || "不出".equals(entry.action())).toList();
        Map<Integer, List<Card>> playedBySeat = new HashMap<>();
        for (int seat = 0; seat < 3; seat++) playedBySeat.put(seat, new ArrayList<>());
        for (TableSnapshot.ActionLog entry : history)
            if (playedBySeat.containsKey(entry.seatId())) playedBySeat.get(entry.seatId()).addAll(entry.cards());

        Map<String, Integer> seats = roleSeats(table);
        float[] countRow = new float[FEATURE_WIDTH];
        oneHot(countRow, playerCount(table, playedBySeat, seats.get("landlord")), 0, 20);
        oneHot(countRow, playerCount(table, playedBySeat, seats.get("landlord_up")), 20, 17);
        oneHot(countRow, playerCount(table, playedBySeat, seats.get("landlord_down")), 37, 17);

        List<float[]> rows = new ArrayList<>(FEATURE_ROWS - 1);
        rows.add(countRow);
        rows.add(cardsToArrayCards(table.hand()));
        rows.add(cardsToArrayCards(otherHand(table, history)));
        rows.add(cardsToArrayCards(remainingBottom(table.bottomCards(), playedBySeat.get(seats.get("landlord")) )));
        rows.add(cardsToArrayCards(playedBySeat.get(seats.get("landlord"))));
        rows.add(cardsToArrayCards(playedBySeat.get(seats.get("landlord_up"))));
        rows.add(cardsToArrayCards(playedBySeat.get(seats.get("landlord_down"))));

        List<TableSnapshot.ActionLog> recent = history.subList(Math.max(0, history.size() - 32), history.size());
        List<TableSnapshot.ActionLog> reversed = new ArrayList<>(recent);
        Collections.reverse(reversed);
        for (int index = 0; index < 32 - reversed.size(); index++) rows.add(filled(-1f));
        for (TableSnapshot.ActionLog entry : reversed)
            rows.add(cardsToArrayCards(entry.cards()));
        if (rows.size() != FEATURE_ROWS - 1)
            throw new IllegalStateException("ONNX 公共特征行数异常: " + (rows.size() + 1));
        return new SharedFeatures(rows);
    }

    private static List<Card> otherHand(TableSnapshot table, List<TableSnapshot.ActionLog> history) {
        Set<Integer> known = new HashSet<>();
        table.hand().forEach(card -> known.add(card.code()));
        history.forEach(entry -> entry.cards().forEach(card -> known.add(card.code())));
        List<Card> result = new ArrayList<>();
        for (int code = 1; code <= 54; code++) if (!known.contains(code)) result.add(Card.fromCode(code));
        return result;
    }

    private static List<Card> remainingBottom(List<Card> bottom, List<Card> landlordPlayed) {
        List<Card> result = new ArrayList<>(bottom);
        for (Card played : landlordPlayed) {
            int index = -1;
            for (int candidate = 0; candidate < result.size(); candidate++)
                if (strength(result.get(candidate).code()) == strength(played.code())) {
                    index = candidate;
                    break;
                }
            if (index >= 0) result.remove(index);
        }
        return result;
    }

    private static int playerCount(TableSnapshot table, Map<Integer, List<Card>> playedBySeat, int seat) {
        if (seat == table.seatId()) return table.hand().size();
        return table.players().stream().filter(player -> player.seatId() == seat)
                .map(TableSnapshot.Player::cardCount).findFirst()
                .orElse(Math.max(0, (seat == table.landlordSeatId() ? 20 : 17)
                        - playedBySeat.getOrDefault(seat, List.of()).size()));
    }

    private static void oneHot(float[] target, int value, int offset, int length) {
        int index = value - 1;
        if (index >= 0 && index < length) target[offset + index] = 1f;
    }

    private static float[] cardsToArray(List<Integer> codes) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int code : codes) counts.merge(strength(code), 1, Integer::sum);
        float[] output = new float[FEATURE_WIDTH];
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int rank = entry.getKey();
            int count = entry.getValue();
            if (rank >= 3 && rank <= 15) {
                int offset = (rank - 3) * 4;
                for (int index = 0; index < Math.min(4, count); index++) output[offset + index] = 1f;
            } else if (rank == 16) output[52] = 1f;
            else if (rank == 17) output[53] = 1f;
        }
        return output;
    }

    private static float[] cardsToArrayCards(List<Card> cards) {
        return cardsToArray(cards.stream().map(Card::code).toList());
    }

    private static float[] filled(float value) {
        float[] output = new float[FEATURE_WIDTH];
        Arrays.fill(output, value);
        return output;
    }

    private static String roleFor(TableSnapshot table) {
        if (table.seatId() == table.landlordSeatId()) return "landlord";
        return (table.seatId() - table.landlordSeatId() + 3) % 3 == 1
                ? "landlord_down" : "landlord_up";
    }

    private static Map<String, Integer> roleSeats(TableSnapshot table) {
        int landlord = table.landlordSeatId();
        Map<String, Integer> result = new LinkedHashMap<>();
        result.put("landlord", landlord);
        result.put("landlord_up", (landlord + 2) % 3);
        result.put("landlord_down", (landlord + 1) % 3);
        return result;
    }

    private static int strength(int code) {
        if (code == 54) return 17;
        if (code == 53) return 16;
        int rank = (code - 1) % 13 + 1;
        if (rank == 1) return 14;
        if (rank == 2) return 15;
        return rank;
    }

    private static boolean stateComplete(TableSnapshot table) {
        if (table.stage() != GameStage.PLAYING || table.landlordSeatId() < 0) return true;
        if (table.players().size() < 3) return false;
        int expected = table.players().stream().mapToInt(player -> {
            int initial = player.seatId() == table.landlordSeatId() ? 20 : 17;
            return Math.max(0, initial - player.cardCount());
        }).sum();
        int observed = table.actionHistory().stream()
                .filter(entry -> "出牌".equals(entry.action()))
                .mapToInt(entry -> entry.cards().size()).sum();
        return expected == observed;
    }

    @PreDestroy
    @Override
    public void close() {
        List<OrtSession> sessions = new ArrayList<>(playSessions.values());
        sessions.addAll(preGameSessions.values());
        sessions.forEach(session -> {
            try {
                session.close();
            } catch (OrtException ignored) {
                // Shutdown should remain best effort.
            }
        });
        playSessions.clear();
        preGameSessions.clear();
    }

    private record SharedFeatures(List<float[]> rows) {
    }

}
