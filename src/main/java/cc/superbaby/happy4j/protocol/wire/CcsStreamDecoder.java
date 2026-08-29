package cc.superbaby.happy4j.protocol.wire;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Stateful TCP decoder handling fragmented and coalesced CCS packets.
 */
public final class CcsStreamDecoder {
    private static final int MIN_FRAME_SIZE = CcsHeader.FIXED_WIRE_SIZE + CMsgHeader.WIRE_SIZE;

    private final CcsFrameCodec codec;
    private final int maxBufferedBytes;
    private byte[] buffer = new byte[4096];
    private int size;

    public CcsStreamDecoder() {
        this(new CcsFrameCodec(), 256 * 1024);
    }

    public CcsStreamDecoder(CcsFrameCodec codec, int maxBufferedBytes) {
        if (codec == null) {
            throw new IllegalArgumentException("codec is required");
        }
        if (maxBufferedBytes < 0xffff) {
            throw new IllegalArgumentException("maxBufferedBytes must hold at least one maximum CCS frame");
        }
        this.codec = codec;
        this.maxBufferedBytes = maxBufferedBytes;
    }

    public synchronized List<CMsgFrame> feed(byte[] chunk) {
        return feed(chunk, null);
    }

    public synchronized List<CMsgFrame> feed(byte[] chunk, byte[] cryptKey) {
        if (chunk == null) {
            throw new IllegalArgumentException("chunk is required");
        }
        append(chunk);
        List<CMsgFrame> frames = new ArrayList<>();
        int consumed = 0;
        while (size - consumed >= 2) {
            int frameLength = ((buffer[consumed] & 0xff) << 8) | (buffer[consumed + 1] & 0xff);
            if (frameLength < MIN_FRAME_SIZE) {
                reset();
                throw new ProtocolException("invalid streamed CCS frame length: " + frameLength);
            }
            if (size - consumed < frameLength) {
                break;
            }
            frames.add(codec.decode(Arrays.copyOfRange(buffer, consumed, consumed + frameLength), cryptKey));
            consumed += frameLength;
        }
        if (consumed > 0) {
            System.arraycopy(buffer, consumed, buffer, 0, size - consumed);
            size -= consumed;
        }
        return List.copyOf(frames);
    }

    public synchronized int bufferedBytes() {
        return size;
    }

    public synchronized void reset() {
        size = 0;
    }

    private void append(byte[] chunk) {
        if ((long) size + chunk.length > maxBufferedBytes) {
            reset();
            throw new ProtocolException("stream buffer exceeds " + maxBufferedBytes + " bytes");
        }
        int required = size + chunk.length;
        if (required > buffer.length) {
            int capacity = buffer.length;
            while (capacity < required) {
                capacity = Math.min(maxBufferedBytes, capacity * 2);
            }
            buffer = Arrays.copyOf(buffer, capacity);
        }
        System.arraycopy(chunk, 0, buffer, size, chunk.length);
        size = required;
    }
}
