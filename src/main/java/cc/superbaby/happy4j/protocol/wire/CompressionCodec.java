package cc.superbaby.happy4j.protocol.wire;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * CCompressAlgorithm-compatible raw LZ4-block and zlib handling.
 */
public final class CompressionCodec {
    public static final int DEFAULT_MAX_OUTPUT = 32 * 1024;

    private CompressionCodec() {
    }

    public static byte[] compress(Compression algorithm, byte[] input) {
        return switch (algorithm) {
            case NONE, RESERVED -> input.clone();
            case LZ4_BLOCK -> encodeLz4Block(input);
            case ZLIB -> deflate(input);
        };
    }

    public static byte[] decompress(Compression algorithm, byte[] input) {
        return decompress(algorithm, input, DEFAULT_MAX_OUTPUT);
    }

    public static byte[] decompress(Compression algorithm, byte[] input, int maxOutput) {
        if (maxOutput <= 0) {
            throw new IllegalArgumentException("maxOutput must be positive");
        }
        return switch (algorithm) {
            case NONE, RESERVED -> input.clone();
            case LZ4_BLOCK -> decodeLz4Block(input, maxOutput);
            case ZLIB -> inflate(input, maxOutput);
        };
    }

    static byte[] encodeLz4Block(byte[] input) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(input.length + 16);
        int[] hashTable = new int[1 << 16];
        Arrays.fill(hashTable, -1);
        int anchor = 0;
        int cursor = 0;
        while (cursor + 4 <= input.length) {
            int hash = hash(readLittleEndianInt(input, cursor));
            int reference = hashTable[hash];
            hashTable[hash] = cursor;
            if (reference < 0 || cursor - reference > 0xffff || !equal4(input, reference, cursor)) {
                cursor++;
                continue;
            }
            int matchLength = 4;
            while (cursor + matchLength < input.length
                    && input[reference + matchLength] == input[cursor + matchLength]) {
                matchLength++;
            }
            writeLz4Sequence(out, input, anchor, cursor, cursor - reference, matchLength);
            cursor += matchLength;
            anchor = cursor;
            if (cursor >= 2 && cursor + 2 < input.length) {
                hashTable[hash(readLittleEndianInt(input, cursor - 2))] = cursor - 2;
            }
        }
        writeLz4LastLiterals(out, input, anchor);
        return out.toByteArray();
    }

    private static void writeLz4Sequence(
            ByteArrayOutputStream out, byte[] input, int anchor, int matchStart, int offset, int matchLength) {
        int literalLength = matchStart - anchor;
        int encodedMatchLength = matchLength - 4;
        out.write((Math.min(literalLength, 15) << 4) | Math.min(encodedMatchLength, 15));
        writeLengthExtension(out, literalLength - 15);
        out.write(input, anchor, literalLength);
        out.write(offset);
        out.write(offset >>> 8);
        writeLengthExtension(out, encodedMatchLength - 15);
    }

    private static void writeLz4LastLiterals(ByteArrayOutputStream out, byte[] input, int anchor) {
        int literalLength = input.length - anchor;
        out.write(Math.min(literalLength, 15) << 4);
        writeLengthExtension(out, literalLength - 15);
        out.write(input, anchor, literalLength);
    }

    private static void writeLengthExtension(ByteArrayOutputStream out, int length) {
        if (length < 0) {
            return;
        }
        while (length >= 255) {
            out.write(255);
            length -= 255;
        }
        out.write(length);
    }

    private static boolean equal4(byte[] input, int left, int right) {
        return input[left] == input[right]
                && input[left + 1] == input[right + 1]
                && input[left + 2] == input[right + 2]
                && input[left + 3] == input[right + 3];
    }

    private static int readLittleEndianInt(byte[] input, int offset) {
        return (input[offset] & 0xff)
                | ((input[offset + 1] & 0xff) << 8)
                | ((input[offset + 2] & 0xff) << 16)
                | ((input[offset + 3] & 0xff) << 24);
    }

    private static int hash(int value) {
        return (value * 0x9e3779b1) >>> 16;
    }

    static byte[] decodeLz4Block(byte[] input, int maxOutput) {
        byte[] out = new byte[Math.min(Math.max(64, input.length * 3), maxOutput)];
        int source = 0;
        int target = 0;
        while (source < input.length) {
            int token = input[source++] & 0xff;
            int literalLength = token >>> 4;
            if (literalLength == 15) {
                int next;
                do {
                    if (source >= input.length) {
                        throw new ProtocolException("truncated LZ4 literal length");
                    }
                    next = input[source++] & 0xff;
                    literalLength = checkedAdd(literalLength, next, maxOutput);
                } while (next == 255);
            }
            out = ensure(out, target + literalLength, maxOutput);
            if (source + literalLength > input.length) {
                throw new ProtocolException("truncated LZ4 literals");
            }
            System.arraycopy(input, source, out, target, literalLength);
            source += literalLength;
            target += literalLength;
            if (source == input.length) {
                return Arrays.copyOf(out, target);
            }
            if (source + 2 > input.length) {
                throw new ProtocolException("truncated LZ4 match offset");
            }
            int offset = (input[source] & 0xff) | ((input[source + 1] & 0xff) << 8);
            source += 2;
            if (offset == 0 || offset > target) {
                throw new ProtocolException("invalid LZ4 match offset: " + offset);
            }
            int matchLength = token & 0x0f;
            if (matchLength == 15) {
                int next;
                do {
                    if (source >= input.length) {
                        throw new ProtocolException("truncated LZ4 match length");
                    }
                    next = input[source++] & 0xff;
                    matchLength = checkedAdd(matchLength, next, maxOutput);
                } while (next == 255);
            }
            matchLength = checkedAdd(matchLength, 4, maxOutput);
            out = ensure(out, target + matchLength, maxOutput);
            int match = target - offset;
            for (int i = 0; i < matchLength; i++) {
                out[target++] = out[match + i];
            }
        }
        return Arrays.copyOf(out, target);
    }

    private static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater();
        try {
            deflater.setInput(input);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(input.length);
            byte[] buffer = new byte[4096];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                if (count == 0 && deflater.needsInput()) {
                    break;
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static byte[] inflate(byte[] input, int maxOutput) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(input);
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(input.length * 3, maxOutput));
            byte[] buffer = new byte[4096];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count > 0) {
                    if (out.size() + count > maxOutput) {
                        throw new ProtocolException("zlib output exceeds " + maxOutput);
                    }
                    out.write(buffer, 0, count);
                } else if (inflater.needsDictionary() || inflater.needsInput()) {
                    throw new ProtocolException("truncated zlib stream");
                }
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new ProtocolException("invalid zlib stream", e);
        } finally {
            inflater.end();
        }
    }

    private static byte[] ensure(byte[] current, int required, int maxOutput) {
        if (required < 0 || required > maxOutput) {
            throw new ProtocolException("decompressed output exceeds " + maxOutput);
        }
        if (required <= current.length) {
            return current;
        }
        int size = current.length;
        while (size < required) {
            size = Math.min(maxOutput, Math.max(size + 1, size * 2));
        }
        return Arrays.copyOf(current, size);
    }

    private static int checkedAdd(int a, int b, int max) {
        long result = (long) a + b;
        if (result > max) {
            throw new ProtocolException("decompressed output exceeds " + max);
        }
        return (int) result;
    }
}
