package cc.superbaby.happy4j.protocol.wire;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Big-endian primitive writer matching the recovered TdrWriteBuf operations.
 */
public final class TdrBufferWriter {
    private final ByteArrayOutputStream output;

    public TdrBufferWriter() {
        this(256);
    }

    public TdrBufferWriter(int expectedSize) {
        output = new ByteArrayOutputStream(Math.max(0, expectedSize));
    }

    public TdrBufferWriter writeInt8(int value) {
        output.write(value);
        return this;
    }

    public TdrBufferWriter writeInt16(int value) {
        output.write(value >>> 8);
        output.write(value);
        return this;
    }

    public TdrBufferWriter writeInt32(int value) {
        output.write(value >>> 24);
        output.write(value >>> 16);
        output.write(value >>> 8);
        output.write(value);
        return this;
    }

    public TdrBufferWriter writeInt64(long value) {
        writeInt32((int) (value >>> 32));
        writeInt32((int) value);
        return this;
    }

    public TdrBufferWriter writeFloat(float value) {
        return writeInt32(Float.floatToRawIntBits(value));
    }

    public TdrBufferWriter writeDouble(double value) {
        return writeInt64(Double.doubleToRawLongBits(value));
    }

    public TdrBufferWriter writeVarUInt32(long value) {
        if (value < 0 || value > 0xffff_ffffL) {
            throw new IllegalArgumentException("value is outside u32: " + value);
        }
        return writeVarUInt64(value);
    }

    public TdrBufferWriter writeVarInt32(int value) {
        return writeVarUInt32(Integer.toUnsignedLong((value << 1) ^ (value >> 31)));
    }

    public TdrBufferWriter writeVarUInt64(long value) {
        while ((value & ~0x7fL) != 0) {
            output.write(((int) value & 0x7f) | 0x80);
            value >>>= 7;
        }
        output.write((int) value);
        return this;
    }

    public TdrBufferWriter writeVarInt64(long value) {
        return writeVarUInt64((value << 1) ^ (value >> 63));
    }

    public TdrBufferWriter writeBytes(byte[] value) {
        if (value == null) {
            throw new IllegalArgumentException("value is required");
        }
        output.writeBytes(value);
        return this;
    }

    public TdrBufferWriter writeCStringFixed(String value, int byteCount) {
        byte[] encoded = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length >= byteCount) {
            throw new ProtocolException("C string requires a NUL within " + byteCount + " bytes");
        }
        output.write(encoded, 0, encoded.length);
        for (int i = encoded.length; i < byteCount; i++) {
            output.write(0);
        }
        return this;
    }

    public int size() {
        return output.size();
    }

    public byte[] toByteArray() {
        return output.toByteArray();
    }
}
