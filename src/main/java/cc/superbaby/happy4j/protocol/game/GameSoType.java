package cc.superbaby.happy4j.protocol.game;

import java.util.Arrays;

/**
 * Named GameSo union discriminators recovered from the wxpkg NZa registry.
 */
public enum GameSoType {
    INITIAL_HAND(1), PLAY(4), SETTLEMENT(5), ORDER(6), LANDLORD_CARDS(7), TRUST(8),
    REVEAL(9), DOUBLE_OPTIONS(10), DOUBLE_RESULT(11), MULTIPLIER_SYNC(12),
    TABLE_TIMEOUT(16), RECONNECT_SNAPSHOT(18), HAND_SYNC(20), LANDLORD_RESULT(27),
    FIRST_TURN(46), ROUND_RESULT(67), DEAL_START(101), ROUND_END(102),
    SECTION_START(1001), SECTION_END(1002), UNKNOWN(-1);

    private final int code;

    GameSoType(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static GameSoType fromCode(int code) {
        return Arrays.stream(values()).filter(value -> value.code == code).findFirst().orElse(UNKNOWN);
    }
}
