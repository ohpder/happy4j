package cc.superbaby.happy4j.protocol.wire;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Bounds-checked counterpart to the recovered TdrReadBuf.
 */
public final class TdrBufferReader {
    private final byte[] input;
    private int position;

    public TdrBufferReader(byte[] input) {
        if (input == null) {
            throw new IllegalArgumentException("input is required");
        }
        this.input = input.clone();
    }

    public int readUInt8() {
        require(1);
        return input[position++] & 0xff;
    }

    public int readInt8() {
        require(1);
        return input[position++];
    }

    public int readUInt16() {
        return Short.toUnsignedInt((short) readInt16());
    }

    public int readInt16() {
        require(2);
        return (short) ((readUInt8() << 8) | readUInt8());
    }

    public long readUInt32() {
        return Integer.toUnsignedLong(readInt32());
    }

    public int readInt32() {
        require(4);
        return (readUInt8() << 24) | (readUInt8() << 16) | (readUInt8() << 8) | readUInt8();
    }

    public long readInt64() {
        require(8);
        return ((long) readInt32() << 32) | Integer.toUnsignedLong(readInt32());
    }

    public float readFloat() {
        return Float.intBitsToFloat(readInt32());
    }

    public double readDouble() {
        return Double.longBitsToDouble(readInt64());
    }

    public long readVarUInt64() {
        long value = 0;
        for (int shift = 0; shift < 64; shift += 7) {
            int next = readUInt8();
            value |= (long) (next & 0x7f) << shift;
            if ((next & 0x80) == 0) {
                return value;
            }
        }
        throw new ProtocolException("varint exceeds 64 bits");
    }

    public long readVarUInt32() {
        long value = readVarUInt64();
        if (value > 0xffff_ffffL) {
            throw new ProtocolException("varint exceeds u32");
        }
        return value;
    }

    public int readVarInt32() {
        int raw = (int) readVarUInt32();
        return (raw >>> 1) ^ -(raw & 1);
    }

    public long readVarInt64() {
        long raw = readVarUInt64();
        return (raw >>> 1) ^ -(raw & 1);
    }

    public byte[] readBytes(int length) {
        require(length);
        byte[] result = Arrays.copyOfRange(input, position, position + length);
        position += length;
        return result;
    }

    public String readCStringFixed(int byteCount) {
        byte[] value = readBytes(byteCount);
        int end = 0;
        while (end < value.length && value[end] != 0) {
            end++;
        }
        return new String(value, 0, end, StandardCharsets.UTF_8);
    }

    public int position() {
        return position;
    }

    public int remaining() {
        return input.length - position;
    }

    private void require(int length) {
        if (length < 0 || position + length > input.length) {
            throw new ProtocolException("TDR input truncated at " + position + ", need " + length
                    + ", remaining " + remaining());
        }
    }
}
