package cc.superbaby.happy4j.protocol.wire;

import org.junit.jupiter.api.Test;

import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TdrBufferTest {
    @Test
    void fixedAndVariablePrimitivesRoundTrip() {
        TdrBufferWriter writer = new TdrBufferWriter()
                .writeInt8(0x12)
                .writeInt16(0x3456)
                .writeInt32(0x789abcde)
                .writeInt64(0x0102030405060708L)
                .writeVarUInt32(300)
                .writeVarInt32(-123)
                .writeCStringFixed("DDZ", 5);

        assertEquals("123456789abcde0102030405060708ac02f50144445a0000",
                HexFormat.of().formatHex(writer.toByteArray()));
        TdrBufferReader reader = new TdrBufferReader(writer.toByteArray());
        assertEquals(0x12, reader.readUInt8());
        assertEquals(0x3456, reader.readUInt16());
        assertEquals(0x789abcde, reader.readInt32());
        assertEquals(0x0102030405060708L, reader.readInt64());
        assertEquals(300, reader.readVarUInt32());
        assertEquals(-123, reader.readVarInt32());
        assertEquals("DDZ", reader.readCStringFixed(5));
        assertEquals(0, reader.remaining());
    }

    @Test
    void nativeGameSoLengthPrefixRoundTrip() {
        byte[] body = {1, 2, 3, 4};
        byte[] frame = GameSoFrameCodec.encodeLengthPrefixed(body);
        assertEquals("000601020304", HexFormat.of().formatHex(frame));
        org.junit.jupiter.api.Assertions.assertArrayEquals(body, GameSoFrameCodec.decodeLengthPrefixed(frame));
    }
}
