package cc.superbaby.happy4j.protocol.wire;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Plaintext outer CCSHead used by the TSDK Lotus TCP transport.
 */
public record CcsHeader(
        int totalLength,
        int protocolVersion,
        int sequence,
        short platform,
        long userId,
        int flags,
        byte[] sessionTicket) {

    public static final int FIXED_WIRE_SIZE = 16;
    public static final int TDR_PROTOCOL_VERSION = 0;
    public static final int PROTOBUF_PROTOCOL_VERSION = 23;
    public static final int FLAG_ENCRYPTED = 0x01;
    /**
     * CLotusSvrConnect::AutoFillCSHead writes 0x11 for every authenticated request.
     */
    public static final int LOTUS_AUTHENTICATED_FLAGS = 0x11;
    public static final int COMPRESSION_SHIFT = 2;
    public static final int COMPRESSION_MASK = 0x0c;

    public CcsHeader {
        if (totalLength < 0 || totalLength > 0xffff) {
            throw new IllegalArgumentException("totalLength is outside u16: " + totalLength);
        }
        if (protocolVersion < Short.MIN_VALUE || protocolVersion > Short.MAX_VALUE) {
            throw new IllegalArgumentException("protocolVersion is outside i16: " + protocolVersion);
        }
        if (userId < 0 || userId > 0xffff_ffffL) {
            throw new IllegalArgumentException("userId is outside u32: " + userId);
        }
        if (flags < 0 || flags > 0xff) {
            throw new IllegalArgumentException("flags is outside u8: " + flags);
        }
        sessionTicket = sessionTicket == null ? new byte[0] : sessionTicket.clone();
        if (sessionTicket.length > 0x7f) {
            throw new IllegalArgumentException("CCS session ticket exceeds recovered 127-byte cap");
        }
    }

    @Override
    public byte[] sessionTicket() {
        return sessionTicket.clone();
    }

    public int encodedLength() {
        return FIXED_WIRE_SIZE + sessionTicket.length;
    }

    public boolean encrypted() {
        return (flags & FLAG_ENCRYPTED) != 0;
    }

    public Compression compression() {
        return Compression.fromId((flags & COMPRESSION_MASK) >>> COMPRESSION_SHIFT);
    }

    public CcsHeader withTotalLength(int length) {
        return new CcsHeader(length, protocolVersion, sequence, platform, userId, flags, sessionTicket);
    }

    public byte[] encode() {
        ByteBuffer out = ByteBuffer.allocate(encodedLength()).order(ByteOrder.BIG_ENDIAN);
        writeTo(out);
        return out.array();
    }

    public void writeTo(ByteBuffer out) {
        out.order(ByteOrder.BIG_ENDIAN)
                .putShort((short) totalLength)
                .putShort((short) protocolVersion)
                .putInt(sequence)
                .putShort(platform)
                .putInt((int) userId)
                .put((byte) flags)
                .put((byte) sessionTicket.length)
                .put(sessionTicket);
    }

    public static CcsHeader decode(ByteBuffer in) {
        if (in.remaining() < FIXED_WIRE_SIZE) {
            throw new ProtocolException("CCSHead truncated: remaining " + in.remaining());
        }
        in.order(ByteOrder.BIG_ENDIAN);
        int totalLength = Short.toUnsignedInt(in.getShort());
        int protocolVersion = in.getShort();
        int sequence = in.getInt();
        short platform = in.getShort();
        long userId = Integer.toUnsignedLong(in.getInt());
        int flags = Byte.toUnsignedInt(in.get());
        int sessionTicketLength = Byte.toUnsignedInt(in.get());
        if (sessionTicketLength > 0x7f) {
            throw new ProtocolException("CCS session ticket length exceeds 127: " + sessionTicketLength);
        }
        if (in.remaining() < sessionTicketLength) {
            throw new ProtocolException("CCS session ticket truncated: need " + sessionTicketLength
                    + ", remaining " + in.remaining());
        }
        byte[] sessionTicket = new byte[sessionTicketLength];
        in.get(sessionTicket);
        return new CcsHeader(totalLength, protocolVersion, sequence, platform, userId, flags, sessionTicket);
    }

    @Override
    public String toString() {
        return "CcsHeader[totalLength=" + totalLength + ", protocolVersion=" + protocolVersion
                + ", sequence=" + sequence + ", platform=" + platform + ", userId=" + userId
                + ", flags=0x" + Integer.toHexString(flags) + ", sessionTicket="
                + Arrays.toString(sessionTicket) + ']';
    }
}
