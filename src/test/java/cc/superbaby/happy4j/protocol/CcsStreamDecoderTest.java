package cc.superbaby.happy4j.protocol.wire;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CcsStreamDecoderTest {
    @Test
    void decodesEveryFragmentationAndCoalescedFrames() {
        CcsFrameCodec codec = new CcsFrameCodec();
        byte[] first = packet(codec, 1, new byte[]{1, 2, 3});
        byte[] second = packet(codec, 2, new byte[]{4, 5});
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        wire.writeBytes(first);
        wire.writeBytes(second);

        for (int chunkSize = 1; chunkSize <= wire.size(); chunkSize++) {
            CcsStreamDecoder stream = new CcsStreamDecoder();
            List<CMsgFrame> frames = new ArrayList<>();
            byte[] bytes = wire.toByteArray();
            for (int offset = 0; offset < bytes.length; offset += chunkSize) {
                int length = Math.min(chunkSize, bytes.length - offset);
                byte[] chunk = new byte[length];
                System.arraycopy(bytes, offset, chunk, 0, length);
                frames.addAll(stream.feed(chunk));
            }
            assertEquals(2, frames.size(), "chunkSize=" + chunkSize);
            assertEquals(1, frames.get(0).messageHeader().sequence());
            assertArrayEquals(new byte[]{4, 5}, frames.get(1).payload());
            assertEquals(0, stream.bufferedBytes());
        }
    }

    private static byte[] packet(CcsFrameCodec codec, int sequence, byte[] payload) {
        CcsHeader outer = new CcsHeader(0, 23, sequence, (short) -1, 0, 0, new byte[0]);
        CMsgHeader inner = CMsgHeader.of(100 + sequence, MessageType.NOTIFY, sequence,
                FunctionalEntity.SERVER, FunctionalEntity.CLIENT, (short) -1, (short) -1);
        return codec.encode(new CMsgFrame(outer, inner, payload));
    }
}
