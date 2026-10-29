package cc.superbaby.happy4j.application;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

import cc.superbaby.happy4j.domain.Card;
import cc.superbaby.happy4j.domain.TableSnapshot;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Direct Java ONNX Runtime adapter for the published PerfectDou models. */
@Service
public final class PerfectDouDecisionService implements AutoCloseable {
    private static final int OBSERVATION_LENGTH = 32_738;
    private static final int VALID_ACTION_ROWS = 150;
    private static final int ACTION_FEATURE_WIDTH = 187;
    private static final int CARD_FEATURE_OFFSET = 7;
    private static final int CARD_COLUMNS = 15;
    private static final Map<String, String> MODELS = Map.of(
            "landlord", "landlord.onnx", "landlord_up", "landlord_up.onnx",
            "landlord_down", "landlord_down.onnx");
    private static final Map<Integer, Integer> COLUMN = new HashMap<>();
    private static final Map<Integer, String> LABEL = new HashMap<>();
    static {
        for (int rank = 3; rank <= 10; rank++) { COLUMN.put(rank, rank - 3); LABEL.put(rank, Integer.toString(rank)); }
        COLUMN.put(11, 8); LABEL.put(11, "J"); COLUMN.put(12, 9); LABEL.put(12, "Q");
        COLUMN.put(13, 10); LABEL.put(13, "K"); COLUMN.put(14, 11); LABEL.put(14, "A");
        COLUMN.put(15, 12); LABEL.put(15, "2"); COLUMN.put(16, 13); LABEL.put(16, "B");
        COLUMN.put(17, 14); LABEL.put(17, "R");
    }

    private final OrtEnvironment environment = OrtEnvironment.getEnvironment();
    private final ObjectMapper json;
    private final String modelRoot;
    private final Map<String, Integer> actionSpace = new ConcurrentHashMap<>();
    private final Map<String, OrtSession> sessions = new ConcurrentHashMap<>();

    public PerfectDouDecisionService(ObjectMapper json,
                                     @Value("${happy4j.model-root}")
                                     String modelRoot) {
        this.json = json;
        this.modelRoot = modelRoot + "perfectdou/";
        loadActionSpace();
    }

    public ServerAiDecisionService.PlayDecision choosePlay(TableSnapshot table, List<List<Integer>> legalMoves) {
        if (legalMoves == null || legalMoves.isEmpty())
            return new ServerAiDecisionService.PlayDecision(List.of(), true, 0, "PerfectDou 没有可提交的合法动作");
        String role = roleFor(table);
        try {
            List<Action> actions = legalMoves.stream().map(move -> action(move, table)).toList();
            float[] scores = infer(role, encodeInput(table, actions));
            int best = 0; float bestScore = -Float.MAX_VALUE;
            for (int index = 0; index < actions.size() && index < VALID_ACTION_ROWS; index++) {
                int id = actions.get(index).id();
                float score = id < scores.length ? scores[id] : -Float.MAX_VALUE;
                if (score > bestScore) { bestScore = score; best = index; }
            }
            List<Integer> selected = List.copyOf(legalMoves.get(best));
            return new ServerAiDecisionService.PlayDecision(selected, selected.isEmpty(), legalMoves.size(),
                    "PerfectDou ONNX " + role);
        } catch (RuntimeException | OrtException failure) {
            throw new IllegalStateException("PerfectDou Java ONNX 推理失败: " + rootMessage(failure), failure);
        }
    }

    private float[] encodeInput(TableSnapshot table, List<Action> actions) {
        float[] input = new float[OBSERVATION_LENGTH];
        State state = state(table);
        encodeCards(input, 0, state.upHand()); encodeCards(input, 180, state.downHand());
        encodeCards(input, 360, state.currentHand()); encodeCards(input, 540, state.notPlayed());
        int first = Math.max(0, state.history().size() - 15);
        for (int row = 0; row < 15 && first + row < state.history().size(); row++)
            encodeCards(input, (4 + row) * 180, state.history().get(first + row));
        encodeCards(input, 19 * 180, state.selfPlayed()); encodeCards(input, 20 * 180, state.upPlayed());
        encodeCards(input, 21 * 180, state.downPlayed()); encodeCards(input, 22 * 180, state.lastUp());
        encodeCards(input, 23 * 180, state.lastDown()); encodeCards(input, 24 * 180, state.lastMove());
        encodeCards(input, 25 * 180, state.bottomCards());
        input[4680] = state.teammateCount() / 17f; input[4681] = state.enemyCount() / 20f;
        input[4682] = state.currentCount() / 20f; input[4683] = state.playedCount() / 54f;
        input[4684] = table.multiplier() / 16f; input[4685] = state.remainingCount() / 20f;
        input[4686] = Math.min(1f, state.bombs() / 16f); input[4687] = 1f;
        for (int index = 0; index < Math.min(actions.size(), VALID_ACTION_ROWS); index++) {
            int offset = 4688 + index * ACTION_FEATURE_WIDTH; Action action = actions.get(index);
            input[offset] = action.id(); input[offset + 1] = action.bomb() ? 1 : 0;
            input[offset + 2] = action.largest() ? 1 : 0;
            input[offset + 3] = action.remaining() == state.teammateCount() ? 1 : 0;
            input[offset + 4] = action.remaining() == state.enemyCount() ? 1 : 0;
            input[offset + 5] = action.remaining() / (float) Math.max(1, state.initialHandSize());
            input[offset + 6] = 1; encodeCards(input, offset + CARD_FEATURE_OFFSET, action.cards());
        }
        return input;
    }

    private float[] infer(String role, float[] input) throws OrtException {
        OrtSession session = sessions.computeIfAbsent(role, this::loadSession);
        String inputName = session.getInputNames().iterator().next();
        try (OnnxTensor tensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(input),
                new long[]{1, OBSERVATION_LENGTH}); OrtSession.Result result = session.run(Map.of(inputName, tensor))) {
            return flatten(result.get(0));
        }
    }

    private OrtSession loadSession(String role) {
        try (InputStream input = new FileInputStream(modelRoot + MODELS.get(role));
             OrtSession.SessionOptions options = new OrtSession.SessionOptions()) {
            options.setIntraOpNumThreads(1); options.setInterOpNumThreads(1);
            return environment.createSession(input.readAllBytes(), options);
        } catch (IOException | OrtException failure) {
            throw new IllegalStateException("加载 PerfectDou 模型失败: " + role, failure);
        }
    }

    private static float[] flatten(OnnxValue value) throws OrtException {
        Object raw = value.getValue();
        if (raw instanceof float[] values) return values;
        if (raw instanceof float[][] values) {
            int size = values.length == 0 ? 0 : values[0].length; float[] result = new float[values.length * size];
            for (int row = 0; row < values.length; row++) System.arraycopy(values[row], 0, result, row * size, size);
            return result;
        }
        throw new IllegalStateException("无法解析 PerfectDou ONNX 输出: " + raw.getClass());
    }

    private Action action(List<Integer> nativeCards, TableSnapshot table) {
        List<Integer> cards = nativeCards.stream().map(PerfectDouDecisionService::envRank).sorted().toList();
        String key = cards.stream().map(PerfectDouDecisionService::label).reduce("", String::concat);
        boolean bomb = cards.size() == 4 || (cards.size() == 2 && cards.contains(16) && cards.contains(17));
        return new Action(actionSpace.getOrDefault(key, cards.isEmpty() ? 620 : 0), cards, bomb,
                false, Math.max(0, table.hand().size() - nativeCards.size()));
    }

    private void loadActionSpace() {
        try (InputStream input = new FileInputStream(modelRoot + "action_space.json")) {
            JsonNode root = json.readTree(input);
            root.properties().forEach(entry -> actionSpace.put(entry.getKey(), entry.getValue().asInt()));
        } catch (IOException failure) { throw new IllegalStateException("加载 PerfectDou action_space.json 失败", failure); }
    }

    private State state(TableSnapshot table) {
        List<TableSnapshot.ActionLog> logs = table.actionHistory().stream()
                .filter(e -> "出牌".equals(e.action()) || "不出".equals(e.action())).toList();
        Map<Integer, List<Integer>> played = new HashMap<>(); for (int seat = 0; seat < 3; seat++) played.put(seat, new ArrayList<>());
        for (TableSnapshot.ActionLog log : logs) if (played.containsKey(log.seatId()))
            played.get(log.seatId()).addAll(log.cards().stream().map(Card::code).toList());
        int landlord = table.landlordSeatId(), up = (landlord + 2) % 3, down = (landlord + 1) % 3;
        List<Integer> current = table.hand().stream().map(Card::code).toList();
        List<List<Integer>> history = logs.stream().map(e -> e.cards().stream().map(Card::code).toList()).toList();
        List<Integer> last = table.lastPlay().stream().map(Card::code).toList();
        List<Integer> unknown = notPlayed(table, history);
        List<Integer> upHand = take(unknown, cardCount(table, up));
        unknown = new ArrayList<>(unknown.subList(Math.min(upHand.size(), unknown.size()), unknown.size()));
        List<Integer> downHand = take(unknown, cardCount(table, down));
        return new State(upHand, downHand, current, notPlayed(table, history), history, played.get(table.seatId()),
                played.get(up), played.get(down), last, last, last, table.bottomCards().stream().map(Card::code).toList(),
                cardCount(table, up), cardCount(table, down), current.size(), played.values().stream().mapToInt(List::size).sum(), 0,
                cardCount(table, table.seatId()), table.seatId() == landlord ? 20 : 17);
    }

    private static List<Integer> notPlayed(TableSnapshot table, List<List<Integer>> history) {
        Set<Integer> known = new HashSet<>(); table.hand().forEach(c -> known.add(c.code()));
        history.forEach(known::addAll); List<Integer> result = new ArrayList<>();
        for (int code = 1; code <= 54; code++) if (!known.contains(code)) result.add(code); return result;
    }
    private static List<Integer> take(List<Integer> source, int count) {
        return List.copyOf(source.subList(0, Math.min(Math.max(0, count), source.size())));
    }
    private static int cardCount(TableSnapshot table, int seat) { return table.players().stream().filter(p -> p.seatId() == seat)
            .mapToInt(TableSnapshot.Player::cardCount).findFirst().orElse(17); }
    private static int envRank(int code) { if (code == 53) return 16; if (code == 54) return 17; int rank = (code - 1) % 13 + 1; return rank == 1 ? 14 : rank == 2 ? 15 : rank; }
    private static String label(int rank) { return LABEL.getOrDefault(rank, ""); }
    private static void encodeCards(float[] target, int offset, List<Integer> cards) {
        Map<Integer, Integer> counts = new LinkedHashMap<>(); for (int code : cards) counts.merge(envRank(code), 1, Integer::sum);
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) { Integer col = COLUMN.get(e.getKey()); if (col == null) continue; int count = Math.min(4, e.getValue());
            for (int row = 0; row < count; row++) target[offset + row * CARD_COLUMNS + col] = 1;
            if (count >= 1) target[offset + 4 * CARD_COLUMNS + col] = 1; if (count >= 2) target[offset + 5 * CARD_COLUMNS + col] = 1;
            if (count >= 3) target[offset + 6 * CARD_COLUMNS + col] = 1; if (count >= 4) target[offset + 7 * CARD_COLUMNS + col] = 1; }
    }
    private static String roleFor(TableSnapshot table) { if (table.seatId() == table.landlordSeatId()) return "landlord"; return table.seatId() == (table.landlordSeatId() + 1) % 3 ? "landlord_down" : "landlord_up"; }
    private record Action(int id, List<Integer> cards, boolean bomb, boolean largest, int remaining) {}
    private record State(List<Integer> upHand, List<Integer> downHand, List<Integer> currentHand, List<Integer> notPlayed,
                         List<List<Integer>> history, List<Integer> selfPlayed, List<Integer> upPlayed, List<Integer> downPlayed,
                         List<Integer> lastUp, List<Integer> lastDown, List<Integer> lastMove, List<Integer> bottomCards,
                         int teammateCount, int enemyCount, int currentCount, int playedCount, int bombs, int remainingCount, int initialHandSize) {}
    private static String rootMessage(Throwable failure) { Throwable root = failure; while (root.getCause() != null) root = root.getCause(); return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage(); }
    @PreDestroy @Override public void close() { sessions.values().forEach(s -> { try { s.close(); } catch (OrtException ignored) {} }); sessions.clear(); }
}
