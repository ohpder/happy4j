package cc.superbaby.happy4j.protocol.wire;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CMsgHeaderTest {
    @Test
    void matchesRecoveredFourteenByteBigEndianLayout() {
        CMsgHeader header = new CMsgHeader(
                0x1234, 1, 0x01020304, 5, 6, (short) 0x0708, (short) 0x090a);

        assertEquals("123400010102030405060708090a", HexFormat.of().formatHex(header.encode()));
        assertEquals(header, CMsgHeader.decode(ByteBuffer.wrap(header.encode())));
        assertEquals(MessageType.RESPONSE, header.knownMessageType().orElseThrow());
    }
}
