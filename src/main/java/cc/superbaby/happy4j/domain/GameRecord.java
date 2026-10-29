package cc.superbaby.happy4j.domain;

import java.time.Instant;
import java.util.List;

public record GameRecord(
        long id,
        Instant finishedAt,
        long gameUin,
        int sceneId,
        int roomId,
        int tableId,
        int localSeatId,
        int landlordSeatId,
        int multiplier,
        long beanChange,
        long beansAfter,
        long factor,
        String result,
        List<Player> players,
        List<Action> actions) {
    public GameRecord {
        result = result == null || result.isBlank() ? "DRAW" : result;
        players = List.copyOf(players == null ? List.of() : players);
        actions = List.copyOf(actions == null ? List.of() : actions);
    }

    public record Player(int seatId, long uin, String nickname, String avatarUrl,
                         long beanChange, long beans, long factor, List<Integer> remainingCards) {
        public Player {
            nickname = nickname == null || nickname.isBlank() ? "玩家 " + (seatId + 1) : nickname;
            avatarUrl = avatarUrl == null ? "" : avatarUrl;
            remainingCards = List.copyOf(remainingCards == null ? List.of() : remainingCards);
        }
    }

    public record Action(Instant occurredAt, int seatId, String player, String action, List<Integer> cards) {
        public Action {
            occurredAt = occurredAt == null ? Instant.EPOCH : occurredAt;
            player = player == null || player.isBlank() ? "玩家 " + (seatId + 1) : player;
            action = action == null ? "" : action;
            cards = List.copyOf(cards == null ? List.of() : cards);
        }
    }
}
