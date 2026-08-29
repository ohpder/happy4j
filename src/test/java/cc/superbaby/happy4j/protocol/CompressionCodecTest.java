package cc.superbaby.happy4j.protocol.wire;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class CompressionCodecTest {
    @Test
    void zlibRoundTrip() {
        byte[] input = "欢乐斗地主-protobuf-protobuf-protobuf".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(input, CompressionCodec.decompress(
                Compression.ZLIB, CompressionCodec.compress(Compression.ZLIB, input)));
    }

    @Test
    void lz4LiteralEncoderAndOverlapDecoderRoundTrip() {
        byte[] input = "literal-only-block".getBytes(StandardCharsets.UTF_8);
        assertArrayEquals(input, CompressionCodec.decompress(
                Compression.LZ4_BLOCK, CompressionCodec.compress(Compression.LZ4_BLOCK, input)));

        byte[] compressed = {0x32, 'a', 'b', 'c', 0x03, 0x00};
        assertArrayEquals("abcabcabc".getBytes(StandardCharsets.UTF_8),
                CompressionCodec.decompress(Compression.LZ4_BLOCK, compressed));
    }

    @Test
    void lz4GreedyCompressorProducesMatches() {
        byte[] input = "abcabcabcabcabcabcabcabcabcabcabcabc".getBytes(StandardCharsets.UTF_8);
        byte[] compressed = CompressionCodec.compress(Compression.LZ4_BLOCK, input);
        org.junit.jupiter.api.Assertions.assertTrue(compressed.length < input.length);
        assertArrayEquals(input, CompressionCodec.decompress(Compression.LZ4_BLOCK, compressed));
    }
}
