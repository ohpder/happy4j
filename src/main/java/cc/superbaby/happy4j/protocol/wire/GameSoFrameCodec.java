package cc.superbaby.happy4j.protocol.wire;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Native game-so TDR framing used by generated Encode methods: u16 total length + body.
 */
public final class GameSoFrameCodec {
    private GameSoFrameCodec() {
    }

    public static byte[] encodeLengthPrefixed(byte[] body) {
        if (body == null) {
            throw new IllegalArgumentException("body is required");
        }
        int total = body.length + 2;
        if (total > 0xffff) {
            throw new ProtocolException("game-so frame exceeds u16 length: " + total);
        }
        return ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN)
                .putShort((short) total).put(body).array();
    }

    public static byte[] decodeLengthPrefixed(byte[] frame) {
        if (frame == null || frame.length < 2) {
            throw new ProtocolException("game-so frame is truncated");
        }
        int total = Short.toUnsignedInt(ByteBuffer.wrap(frame).order(ByteOrder.BIG_ENDIAN).getShort());
        if (total != frame.length) {
            throw new ProtocolException("game-so length mismatch: header=" + total + ", actual=" + frame.length);
        }
        return Arrays.copyOfRange(frame, 2, frame.length);
    }
}
