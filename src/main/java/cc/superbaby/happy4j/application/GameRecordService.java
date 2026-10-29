package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.ClientSession;
import cc.superbaby.happy4j.domain.GameRecord;
import cc.superbaby.happy4j.domain.GameRecordPage;
import cc.superbaby.happy4j.domain.GameRecordStats;
import cc.superbaby.happy4j.domain.TableSnapshot;
import cc.superbaby.happy4j.infrastructure.persistence.entity.GameRoundRecordEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameClientSessionRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameRoundRecordRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class GameRecordService {
    private final GameRoundRecordRepository records;
    private final GameClientSessionRepository sessions;
    private final ObjectMapper json;

    public GameRecordService(GameRoundRecordRepository records, GameClientSessionRepository sessions,
                             ObjectMapper json) {
        this.records = records;
        this.sessions = sessions;
        this.json = json;
    }

    @Transactional
    public void recordSettlement(ClientSession session, TableSnapshot table) {
        if (session.gameCredential() == null || !table.settlement().available()) return;
        String roundKey = roundKey(table);
        if (records.existsForRound(session.id(), roundKey)) return;
        var persistedSession = sessions.findByIdAndUserId(session.id(), session.ownerId()).orElse(null);
        if (persistedSession == null) return;
        TableSnapshot.PlayerResult local = table.settlement().players().stream()
                .filter(value -> value.seatId() == table.seatId()).findFirst().orElse(null);
        long beanChange = local == null ? 0 : local.beanChange();
        long beansAfter = local == null ? 0 : local.beans();
        long factor = local == null ? 0 : local.factor();
        String result = beanChange > 0 ? "WIN" : beanChange < 0 ? "LOSS" : "DRAW";
        records.save(new GameRoundRecordEntity(persistedSession.getUser(), persistedSession, roundKey,
                session.gameCredential().uin(), table.sceneId(), table.roomId(), table.tableId(), table.seatId(),
                table.landlordSeatId(), table.multiplier(), beanChange, beansAfter, factor, result,
                detailJson(table), Instant.now()));
    }

    @Transactional(readOnly = true)
    public GameRecordPage page(UUID userId, int limit) {
        List<GameRoundRecordEntity> all = records.findByUser_IdOrderByFinishedAtDesc(userId);
        return page(all, limit);
    }

    @Transactional(readOnly = true)
    public GameRecordPage page(UUID userId, UUID sessionId, int limit) {
        return page(records.findByUser_IdAndSession_IdOrderByFinishedAtDesc(userId, sessionId), limit);
    }

    private GameRecordPage page(List<GameRoundRecordEntity> all, int limit) {
        List<GameRecord> latest = all.stream().limit(Math.max(1, Math.min(100, limit)))
                .map(this::view).toList();
        return new GameRecordPage(stats(all), latest);
    }

    @Transactional(readOnly = true)
    public List<GameRecord> records(UUID userId, int limit) {
        return page(userId, limit).records();
    }

    @Transactional(readOnly = true)
    public GameRecordStats stats(UUID userId) {
        return stats(records.findByUser_IdOrderByFinishedAtDesc(userId));
    }

    private GameRecord view(GameRoundRecordEntity entity) {
        List<GameRecord.Player> players = new ArrayList<>();
        List<GameRecord.Action> actions = new ArrayList<>();
        try {
            JsonNode root = json.readTree(entity.getDetailJson());
            JsonNode playerValues = root.path("players");
            if (playerValues.isArray()) for (JsonNode value : playerValues) {
                List<Integer> cards = new ArrayList<>();
                JsonNode cardValues = value.path("remainingCards");
                if (cardValues.isArray()) cardValues.forEach(card -> cards.add(card.asInt()));
                players.add(new GameRecord.Player(value.path("seatId").asInt(), value.path("uin").asLong(),
                        value.path("nickname").asString(), value.path("avatarUrl").asString(),
                        value.path("beanChange").asLong(), value.path("beans").asLong(),
                        value.path("factor").asLong(), cards));
            }
            JsonNode actionValues = root.path("actions");
            if (actionValues.isArray()) for (JsonNode value : actionValues) {
                List<Integer> cards = new ArrayList<>();
                JsonNode cardValues = value.path("cards");
                if (cardValues.isArray()) cardValues.forEach(card -> cards.add(card.asInt()));
                actions.add(new GameRecord.Action(parseInstant(value.path("occurredAt").asString()),
                        value.path("seatId").asInt(), value.path("player").asString(),
                        value.path("action").asString(), cards));
            }
        } catch (Exception ignored) {
            // A malformed detail must not hide the scalar result from the history list.
        }
        return new GameRecord(entity.getId() == null ? 0 : entity.getId(), entity.getFinishedAt(),
                entity.getGameUin(), entity.getSceneId(), entity.getRoomId(), entity.getTableId(),
                entity.getLocalSeatId(), entity.getLandlordSeatId(), entity.getMultiplier(),
                entity.getBeanChange(), entity.getBeansAfter(), entity.getFactor(), entity.getResult(),
                players, actions);
    }

    private GameRecordStats stats(List<GameRoundRecordEntity> values) {
        long wins = values.stream().filter(value -> "WIN".equals(value.getResult())).count();
        long losses = values.stream().filter(value -> "LOSS".equals(value.getResult())).count();
        long draws = values.size() - wins - losses;
        long totalChange = values.stream().mapToLong(GameRoundRecordEntity::getBeanChange).sum();
        long bestWin = values.stream().mapToLong(GameRoundRecordEntity::getBeanChange).filter(value -> value > 0)
                .max().orElse(0);
        long worstLoss = values.stream().mapToLong(GameRoundRecordEntity::getBeanChange).filter(value -> value < 0)
                .min().orElse(0);
        double winRate = values.isEmpty() ? 0 : wins * 100.0 / values.size();
        return new GameRecordStats(values.size(), wins, losses, draws, winRate,
                totalChange, bestWin, worstLoss);
    }

    private String detailJson(TableSnapshot table) {
        try {
            List<GameRecord.Player> players = table.settlement().players().stream().map(result -> {
                TableSnapshot.Player player = table.players().stream()
                        .filter(value -> value.seatId() == result.seatId()).findFirst().orElse(null);
                return new GameRecord.Player(result.seatId(), player == null ? 0 : player.uin(),
                        player == null ? "" : player.nickname(), player == null ? "" : player.avatarUrl(),
                        result.beanChange(), result.beans(), result.factor(),
                        result.remainingCards().stream().map(value -> value.code()).toList());
            }).toList();
            List<GameRecord.Action> actions = table.actionHistory().stream().map(value ->
                    new GameRecord.Action(value.occurredAt(), value.seatId(), value.player(), value.action(),
                            value.cards().stream().map(card -> card.code()).toList())).toList();
            return json.writeValueAsString(new Detail(players, actions));
        } catch (Exception failure) {
            throw new IllegalStateException("对局记录序列化失败", failure);
        }
    }

    private static String roundKey(TableSnapshot table) {
        return table.sceneId() + ":" + table.roomId() + ":" + table.tableId() + ":"
                + table.actionHistory().size() + ":" + table.multiplier();
    }

    private static Instant parseInstant(String value) {
        try {
            return value == null || value.isBlank() ? Instant.EPOCH : Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private record Detail(List<GameRecord.Player> players, List<GameRecord.Action> actions) {
    }
}
