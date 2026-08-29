package cc.superbaby.happy4j.protocol.game;

import java.util.Arrays;

public enum DdzPattern {
    PASS(0), SINGLE(1), STRAIGHT(2), PAIR(3), PAIR_STRAIGHT(4), TRIPLE(5), TRIPLE_STRAIGHT(6),
    TRIPLE_WITH_SINGLE(7), TRIPLE_WITH_PAIR(8), AIRPLANE_WITH_SINGLES(9), AIRPLANE_WITH_PAIRS(10),
    FOUR_WITH_SINGLES(13), FOUR_WITH_PAIRS(14), BOMB(15), ROCKET(25), UNKNOWN(-1);
    private final int code;

    DdzPattern(int code) {
        this.code = code;
    }

    public int code() {
        return code;
    }

    public static DdzPattern fromCode(int code) {
        return Arrays.stream(values()).filter(value -> value.code == code).findFirst().orElse(UNKNOWN);
    }
}
