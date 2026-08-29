package cc.superbaby.happy4j.protocol.wire;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CcsFrameCodecTest {
    @Test
    void plainFrameMatchesRecoveredHeaderOrdering() {
        CcsHeader transport = new CcsHeader(
                0, 23, 0x10203040, (short) 3, 0xaabbccddL, 0, new byte[]{1, 2});
        CMsgHeader message = CMsgHeader.of(
                0x1234, MessageType.REQUEST, 0x01020304,
                FunctionalEntity.CLIENT, FunctionalEntity.SERVER, (short) -1, (short) -1);
        byte[] packet = new CcsFrameCodec().encode(new CMsgFrame(transport, message, new byte[]{9, 8, 7}));

        assertEquals(37, packet.length);
        assertEquals("00250017102030400003aabbccdd00020102",
                HexFormat.of().formatHex(packet, 0, transport.encodedLength()));
        assertEquals("0005090807", HexFormat.of().formatHex(packet,
                transport.encodedLength() + CMsgHeader.WIRE_SIZE, packet.length));
        CMsgFrame decoded = new CcsFrameCodec().decode(packet);
        assertEquals(message, decoded.messageHeader());
        assertArrayEquals(new byte[]{9, 8, 7}, decoded.payload());
        assertEquals(37, decoded.transportHeader().totalLength());
    }

    @Test
    void grpcFrameUsesProtobufLengthRatherThanCUnknownTotalLength() {
        CcsHeader transport = new CcsHeader(0, 23, -1, (short) -3, 7, 0, new byte[0]);
        CMsgHeader message = CMsgHeader.of(18493, MessageType.REQUEST, 9,
                FunctionalEntity.CLIENT, FunctionalEntity.LOGIC_SERVICE_SERVER, (short) 0, (short) 0);
        byte[] packet = new CcsFrameCodec().encodeGrpc(
                new CMsgFrame(transport, message, new byte[]{9, 8, 7}), null);

        int prefix = transport.encodedLength() + CMsgHeader.WIRE_SIZE;
        assertEquals("0003090807", HexFormat.of().formatHex(packet, prefix, packet.length));
    }

    @Test
    void lotusHeaderMatchesAutoFillCsHeadFields() {
        CcsHeader header = new CcsHeader(0, CcsHeader.TDR_PROTOCOL_VERSION, -1,
                (short) -3, 0x1234_5678L, CcsHeader.LOTUS_AUTHENTICATED_FLAGS,
                new byte[]{0x11, 0x22, 0x33});

        assertEquals("00000000fffffffffffD123456781103112233".toLowerCase(),
                HexFormat.of().formatHex(header.encode()));
        assertEquals(19, header.encodedLength());
        assertEquals(Compression.NONE, header.compression());
    }

    @Test
    void encryptedAndCompressedFrameRoundTrip() {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        int flags = 0x10 | CcsHeader.FLAG_ENCRYPTED
                | (Compression.ZLIB.id() << CcsHeader.COMPRESSION_SHIFT);
        CcsHeader transport = new CcsHeader(0, 23, 77, (short) -1, 363, flags, new byte[]{4, 5});
        CMsgHeader message = CMsgHeader.of(16601, MessageType.REQUEST, 12,
                FunctionalEntity.CLIENT, FunctionalEntity.OLYMPIC_SERVER, (short) -1, (short) -1);
        byte[] payload = "protobuf payload protobuf payload".getBytes(StandardCharsets.UTF_8);

        byte[] packet = new CcsFrameCodec().encode(new CMsgFrame(transport, message, payload), key);
        CMsgFrame decoded = new CcsFrameCodec().decode(packet, key);
        assertEquals(message, decoded.messageHeader());
        assertArrayEquals(payload, decoded.payload());
    }
}
