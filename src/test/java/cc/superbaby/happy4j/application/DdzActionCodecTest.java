package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.protocol.game.MiniGameActionProtocol;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DdzActionCodecTest {
    private final MiniGameActionProtocol protocol = new MiniGameActionProtocol();

    @Test
    void encodesSingleCardAsGameBasePmSubtypeFour() {
        assertThat(hex(protocol.give(List.of(1), false)))
                .isEqualTo("00000004000000000001000000010c010000000000000000000000000000000000");
    }

    @Test
    void encodesPassOrderAndDoubleUsingRecoveredUnionLayouts() {
        assertThat(hex(protocol.give(List.of(), true)))
                .isEqualTo("0000000400000000000000000000000000000000000000010000000000000000");
        assertThat(hex(protocol.order(1)))
                .isEqualTo("000000060000000001000000000000000000000000");
        assertThat(hex(protocol.multiple(2, 1)))
                .isEqualTo("0000000b000000000102000000000000000000000000");
        assertThat(hex(protocol.multiple(1, 1)))
                .isEqualTo("0000000b000000000101000000000000000000000000");
        assertThat(hex(protocol.trust(true))).isEqualTo("000000080000000001");
    }

    @Test
    void rejectsAnInvalidCombinationBeforeSendingIt() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> protocol.give(List.of(1, 18, 45), false))
                .hasMessageContaining("不构成经典斗地主合法牌型");
    }

    @Test
    void acceptsSmallAndBigJokerAsRocket() {
        var combination = protocol.classifyCards(List.of(53, 54));

        assertThat(combination.pattern()).isEqualTo(cc.superbaby.happy4j.protocol.game.DdzPattern.ROCKET);
        assertThat(combination.mainRank()).isEqualTo(15);
        assertThat(protocol.give(List.of(53, 54), false)).isNotEmpty();
    }

    @Test
    void comparesSelectedCombinationWithTheLastEffectivePlay() {
        assertThat(protocol.canBeat(protocol.classifyCards(List.of(4)), 1, 1, 1, 1)).isTrue();
        assertThat(protocol.canBeat(protocol.classifyCards(List.of(4, 17)), 2, 1, 1, 1)).isFalse();
        assertThat(protocol.canBeat(protocol.classifyCards(List.of(3, 16, 29, 42)), 4,
                1, 15, 1)).isTrue();
        assertThat(protocol.canBeat(protocol.classifyCards(List.of(4, 5, 6, 7, 8)), 5,
                2, 5, 5)).isTrue();
    }

    private static String hex(byte[] value) {
        return HexFormat.of().formatHex(value);
    }
}
