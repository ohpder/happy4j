package cc.superbaby.happy4j.protocol;

import cc.superbaby.happy4j.protocol.game.GameClientTlvCodec;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class GameClientTlvCodecTest {
    @Test
    void matchesCurrentApkTransparentAndAbilityUnionWireValues() {
        var values = GameClientTlvCodec.sitDownTlvs();

        assertThat(values).hasSize(2);
        assertThat(values.get(0).type()).isEqualTo(20012);
        assertThat(HexFormat.of().formatHex(values.get(0).value()))
                .isEqualTo("8a050708031203980102");
        assertThat(values.get(1).type()).isEqualTo(20017);
        assertThat(HexFormat.of().formatHex(values.get(1).value()))
                .isEqualTo("ca050b08effcffbf01109a85ce60");
    }

    @Test
    void explicitUnityTransparencyRemainsAvailableOutsideMiniGamePath() {
        assertThat(GameClientTlvCodec.transparent3d(false).type()).isEqualTo(20012);
        assertThat(HexFormat.of().formatHex(GameClientTlvCodec.transparent3d(false).value()))
                .isEqualTo("8a050708031203980102");
    }
}
