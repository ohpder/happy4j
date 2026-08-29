package cc.superbaby.happy4j.protocol;

import cc.superbaby.happy4j.protocol.game.DdzPattern;
import cc.superbaby.happy4j.protocol.game.GameMessage;
import cc.superbaby.happy4j.protocol.game.GameSoType;
import cc.superbaby.happy4j.protocol.game.RoomEventType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolTypeRegistryTest {
    @Test
    void mapsWireCodesToNamedBoundaryTypes() {
        assertThat(GameSoType.fromCode(18)).isEqualTo(GameSoType.RECONNECT_SNAPSHOT);
        assertThat(RoomEventType.fromCode(11)).isEqualTo(RoomEventType.BALANCE_CHANGED);
        assertThat(DdzPattern.fromCode(25)).isEqualTo(DdzPattern.ROCKET);
        assertThat(GameMessage.QUERY_REPLAY.matches(15806)).isTrue();
    }
}
