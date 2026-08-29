package cc.superbaby.happy4j.protocol.wire;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Complete offline encoder/decoder for CCSHead + CMsgHead + payload.
 */
public final class CcsFrameCodec {
    private static final int UNKNOWN_PARAMETER_PREFIX_SIZE = 2;
    private final int maxDecompressedPayload;

    public CcsFrameCodec() {
        this(CompressionCodec.DEFAULT_MAX_OUTPUT);
    }

    public CcsFrameCodec(int maxDecompressedPayload) {
        if (maxDecompressedPayload < CMsgHeader.WIRE_SIZE) {
            throw new IllegalArgumentException("maxDecompressedPayload is too small");
        }
        this.maxDecompressedPayload = maxDecompressedPayload;
    }

    public byte[] encode(CMsgFrame frame) {
        return encode(frame, null);
    }

    public byte[] encode(CMsgFrame frame, byte[] cryptKey) {
        return encode(frame, cryptKey, true);
    }

    /**
     * TSDK's CGRPCMsgTransaction is the one exception to CUnknowMsgPara framing:
     * its two-byte prefix contains the protobuf size rather than size + prefix.
     */
    public byte[] encodeGrpc(CMsgFrame frame, byte[] cryptKey) {
        return encode(frame, cryptKey, false);
    }

    private byte[] encode(CMsgFrame frame, byte[] cryptKey, boolean prefixIncludesSelf) {
        byte[] payload = frame.payload();
        int parameterLength = payload.length + UNKNOWN_PARAMETER_PREFIX_SIZE;
        if (parameterLength > Short.MAX_VALUE) {
            throw new ProtocolException("CUnknowMsgPara exceeds signed-short length: " + parameterLength);
        }
        ByteBuffer message = ByteBuffer.allocate(CMsgHeader.WIRE_SIZE + parameterLength)
                .order(ByteOrder.BIG_ENDIAN);
        frame.messageHeader().writeTo(message);
        // CUnknowMsgPara_setDecodeBuf prepends its own network-order length, including
        // these two bytes, before CLotusSvrConnect encrypts the CMsg body.
        message.putShort((short) (prefixIncludesSelf ? parameterLength : payload.length));
        message.put(payload);

        CcsHeader requested = frame.transportHeader();
        byte[] body = CompressionCodec.compress(requested.compression(), message.array());
        if (requested.encrypted()) {
            body = QqTeaCipher.encrypt(body, requireCryptKey(cryptKey));
        }

        int totalLength = requested.encodedLength() + body.length;
        if (totalLength > 0xffff) {
            throw new ProtocolException("CCS packet exceeds u16 length: " + totalLength);
        }
        CcsHeader actual = requested.withTotalLength(totalLength);
        ByteBuffer output = ByteBuffer.allocate(totalLength).order(ByteOrder.BIG_ENDIAN);
        actual.writeTo(output);
        output.put(body);
        return output.array();
    }

    public CMsgFrame decode(byte[] packet) {
        return decode(packet, null);
    }

    public CMsgFrame decode(byte[] packet, byte[] cryptKey) {
        ByteBuffer input = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN);
        CcsHeader header = CcsHeader.decode(input);
        if (header.totalLength() < header.encodedLength() + CMsgHeader.WIRE_SIZE) {
            throw new ProtocolException("invalid CCS total length: " + header.totalLength());
        }
        if (header.totalLength() > packet.length) {
            throw new ProtocolException("truncated CCS packet: header says " + header.totalLength()
                    + ", have " + packet.length);
        }
        int bodyLength = header.totalLength() - header.encodedLength();
        byte[] body = new byte[bodyLength];
        input.get(body);
        if (header.encrypted()) {
            body = QqTeaCipher.decrypt(body, requireCryptKey(cryptKey));
        }
        body = CompressionCodec.decompress(header.compression(), body, maxDecompressedPayload);
        if (body.length < CMsgHeader.WIRE_SIZE + UNKNOWN_PARAMETER_PREFIX_SIZE) {
            throw new ProtocolException("decoded CMsg is shorter than CMsgHead + CUnknowMsgPara prefix");
        }
        ByteBuffer message = ByteBuffer.wrap(body).order(ByteOrder.BIG_ENDIAN);
        CMsgHeader messageHeader = CMsgHeader.decode(message);
        int parameterLength = Short.toUnsignedInt(message.getShort());
        if (parameterLength < UNKNOWN_PARAMETER_PREFIX_SIZE) {
            throw new ProtocolException("invalid CUnknowMsgPara length: " + parameterLength);
        }
        int payloadLength = parameterLength - UNKNOWN_PARAMETER_PREFIX_SIZE;
        if (payloadLength > message.remaining()) {
            throw new ProtocolException("truncated CUnknowMsgPara: need " + payloadLength
                    + ", remaining " + message.remaining());
        }
        byte[] payload = new byte[payloadLength];
        message.get(payload);
        return new CMsgFrame(header, messageHeader, payload);
    }

    /**
     * Returns the total first-frame size without consuming or decrypting it.
     */
    public static int peekFrameLength(byte[] prefix) {
        if (prefix.length < 2) {
            throw new ProtocolException("need two bytes for CCS length");
        }
        return Short.toUnsignedInt(ByteBuffer.wrap(prefix, 0, 2)
                .order(ByteOrder.BIG_ENDIAN).getShort());
    }

    private static byte[] requireCryptKey(byte[] cryptKey) {
        if (cryptKey == null) {
            throw new ProtocolException("frame is encrypted but no 16-byte random key was supplied");
        }
        return Arrays.copyOf(cryptKey, cryptKey.length);
    }
}
