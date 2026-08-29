package cc.superbaby.happy4j.domain;

public record Card(int code, String rank, String suit, boolean selected) {
    public static Card fromCode(int code) {
        if (code == 53) return new Card(code, "小王", "", false);
        if (code == 54) return new Card(code, "大王", "", false);
        int rank = code >= 1 && code <= 52 ? (code - 1) % 13 + 1 : code & 0x0f;
        int suit = code >= 1 && code <= 52 ? (code - 1) / 13 : (code >>> 4) & 0x0f;
        String rankName = switch (rank) {
            case 1 -> "A";
            case 2 -> "2";
            case 11 -> "J";
            case 12 -> "Q";
            case 13 -> "K";
            case 14 -> "小王";
            case 15 -> "大王";
            default -> Integer.toString(rank);
        };
        String suitName = switch (suit) {
            case 0 -> "♠";
            case 1 -> "♥";
            case 2 -> "♣";
            case 3 -> "♦";
            default -> "";
        };
        return new Card(code, rankName, suitName, false);
    }
}
