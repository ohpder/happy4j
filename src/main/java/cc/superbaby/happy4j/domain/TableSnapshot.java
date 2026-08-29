package cc.superbaby.happy4j.domain;

import java.util.List;
import java.time.Instant;

public record TableSnapshot(
        int sceneId,
        int roomId,
        int tableId,
        int seatId,
        int turnSeatId,
        int landlordSeatId,
        List<Card> hand,
        List<Card> lastPlay,
        int lastPlayType,
        int lastPlayRank,
        int lastPlaySeatId,
        List<Card> bottomCards,
        List<Player> players,
        List<ActionLog> actionHistory,
        GameStage stage,
        List<TableAction> availableActions,
        int multiplier,
        boolean trusted,
        Settlement settlement,
        int superDoubleItemId,
        int operationSeconds,
        String prompt) {
    public TableSnapshot {
        hand = List.copyOf(hand);
        lastPlay = List.copyOf(lastPlay);
        bottomCards = List.copyOf(bottomCards);
        players = List.copyOf(players);
        actionHistory = List.copyOf(actionHistory);
        availableActions = List.copyOf(availableActions);
        settlement = settlement == null ? Settlement.empty() : settlement;
        superDoubleItemId = Math.max(0, superDoubleItemId);
        operationSeconds = Math.max(0, operationSeconds);
    }

    public TableSnapshot(int sceneId, int roomId, int tableId, int seatId, int turnSeatId,
                         int landlordSeatId, List<Card> hand, List<Card> lastPlay, int lastPlayType,
                         int lastPlayRank, int lastPlaySeatId, List<Card> bottomCards,
                         List<Player> players, List<ActionLog> actionHistory, GameStage stage,
                         List<TableAction> availableActions, int multiplier, boolean trusted, String prompt) {
        this(sceneId, roomId, tableId, seatId, turnSeatId, landlordSeatId, hand, lastPlay,
                lastPlayType, lastPlayRank, lastPlaySeatId, bottomCards, players, actionHistory,
                stage, availableActions, multiplier, trusted, Settlement.empty(),
                0, defaultOperationSeconds(stage, turnSeatId, lastPlay), prompt);
    }

    public TableSnapshot(int sceneId, int roomId, int tableId, int seatId, int turnSeatId,
                         int landlordSeatId, List<Card> hand, List<Card> lastPlay, int lastPlayType,
                         int lastPlayRank, int lastPlaySeatId, List<Card> bottomCards,
                         List<Player> players, List<ActionLog> actionHistory, GameStage stage,
                         List<TableAction> availableActions, int multiplier, boolean trusted,
                         Settlement settlement, String prompt) {
        this(sceneId, roomId, tableId, seatId, turnSeatId, landlordSeatId, hand, lastPlay,
                lastPlayType, lastPlayRank, lastPlaySeatId, bottomCards, players, actionHistory,
                stage, availableActions, multiplier, trusted, settlement,
                0, defaultOperationSeconds(stage, turnSeatId, lastPlay), prompt);
    }

    public TableSnapshot(int sceneId, int roomId, int tableId, int seatId, int turnSeatId,
                         int landlordSeatId, List<Card> hand, List<Card> lastPlay, int lastPlayType,
                         int lastPlayRank, int lastPlaySeatId, List<Card> bottomCards,
                         List<Player> players, List<ActionLog> actionHistory, GameStage stage,
                         List<TableAction> availableActions, int multiplier, boolean trusted,
                         Settlement settlement, int superDoubleItemId, String prompt) {
        this(sceneId, roomId, tableId, seatId, turnSeatId, landlordSeatId, hand, lastPlay,
                lastPlayType, lastPlayRank, lastPlaySeatId, bottomCards, players, actionHistory,
                stage, availableActions, multiplier, trusted, settlement, superDoubleItemId,
                defaultOperationSeconds(stage, turnSeatId, lastPlay), prompt);
    }

    public TableSnapshot(int sceneId, int roomId, int tableId, int seatId,
                         int turnSeatId, int landlordSeatId, List<Card> hand, List<Card> lastPlay,
                         List<Card> bottomCards, List<Player> players, GameStage stage,
                         List<TableAction> availableActions, int multiplier, String prompt) {
        this(sceneId, roomId, tableId, seatId, turnSeatId, landlordSeatId, hand, lastPlay,
                0, 0, -1, bottomCards, players, List.of(), stage, availableActions, multiplier, false, prompt);
    }

    private static int defaultOperationSeconds(GameStage stage, int turnSeatId, List<Card> lastPlay) {
        if (turnSeatId < 0) return 0;
        return switch (stage) {
            case CALLING_LANDLORD -> 15;
            case DOUBLING -> 6;
            case PLAYING -> lastPlay == null || lastPlay.isEmpty() ? 30 : 25;
            default -> 0;
        };
    }

    public static TableSnapshot empty() {
        return new TableSnapshot(0, 0, 0, -1, -1, -1,
                List.of(), List.of(), List.of(), List.of(), GameStage.IDLE, List.of(), 1,
                "等待进入牌桌");
    }

    public record Player(long uin, String nickname, int seatId, int cardCount, long beans,
                         boolean ready, String avatarUrl, long lastBeanChange) {
        public Player(long uin, String nickname, int seatId, int cardCount, long beans, boolean ready) {
            this(uin, nickname, seatId, cardCount, beans, ready, "", 0);
        }

        public Player(long uin, String nickname, int seatId, int cardCount, long beans,
                      boolean ready, String avatarUrl) {
            this(uin, nickname, seatId, cardCount, beans, ready, avatarUrl, 0);
        }

        public Player {
            avatarUrl = PlayerProfile.normalizeAvatarUrl(avatarUrl);
        }
    }

    public record ActionLog(Instant occurredAt, int seatId, String player, String action,
                            List<Card> cards) {
        public ActionLog {
            occurredAt = occurredAt == null ? Instant.now() : occurredAt;
            player = player == null || player.isBlank() ? "玩家 " + (seatId + 1) : player;
            cards = List.copyOf(cards == null ? List.of() : cards);
        }
    }

    public record Settlement(int resultFlag, List<PlayerResult> players) {
        public Settlement {
            players = List.copyOf(players == null ? List.of() : players);
        }

        public static Settlement empty() {
            return new Settlement(0, List.of());
        }

        public boolean available() {
            return !players.isEmpty();
        }
    }

    public record PlayerResult(int seatId, long beanChange, long beans, long factor,
                               List<Card> remainingCards) {
        public PlayerResult {
            remainingCards = List.copyOf(remainingCards == null ? List.of() : remainingCards);
        }
    }
}
