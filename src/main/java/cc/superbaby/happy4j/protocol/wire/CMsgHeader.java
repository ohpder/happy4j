package cc.superbaby.happy4j.protocol.wire;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact 14-byte big-endian TSDK CMsgHead.
 *
 * <pre>
 * u16 msgId, i16 msgType, i32 sequence,
 * u8 sourceEntity, u8 destinationEntity, i16 sourceId, i16 destinationId
 * </pre>
 */
public record CMsgHeader(
        int messageId,
        int messageType,
        int sequence,
        int sourceEntity,
        int destinationEntity,
        short sourceId,
        short destinationId) {

    public static final int WIRE_SIZE = 14;

    public CMsgHeader {
        requireUnsigned(messageId, 16, "messageId");
        if (messageType < Short.MIN_VALUE || messageType > Short.MAX_VALUE) {
            throw new IllegalArgumentException("messageType is outside i16: " + messageType);
        }
        requireUnsigned(sourceEntity, 8, "sourceEntity");
        requireUnsigned(destinationEntity, 8, "destinationEntity");
    }

    public static CMsgHeader of(
            int messageId,
            MessageType messageType,
            int sequence,
            int sourceEntity,
            int destinationEntity,
            short sourceId,
            short destinationId) {
        return new CMsgHeader(messageId, Objects.requireNonNull(messageType).wireValue(), sequence,
                sourceEntity, destinationEntity, sourceId, destinationId);
    }

    public Optional<MessageType> knownMessageType() {
        try {
            return Optional.of(MessageType.fromWireValue(messageType));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public byte[] encode() {
        ByteBuffer out = ByteBuffer.allocate(WIRE_SIZE).order(ByteOrder.BIG_ENDIAN);
        writeTo(out);
        return out.array();
    }

    public void writeTo(ByteBuffer out) {
        out.order(ByteOrder.BIG_ENDIAN)
                .putShort((short) messageId)
                .putShort((short) messageType)
                .putInt(sequence)
                .put((byte) sourceEntity)
                .put((byte) destinationEntity)
                .putShort(sourceId)
                .putShort(destinationId);
    }

    public static CMsgHeader decode(ByteBuffer in) {
        if (in.remaining() < WIRE_SIZE) {
            throw new ProtocolException("CMsgHead truncated: need 14, remaining " + in.remaining());
        }
        in.order(ByteOrder.BIG_ENDIAN);
        return new CMsgHeader(
                Short.toUnsignedInt(in.getShort()),
                in.getShort(),
                in.getInt(),
                Byte.toUnsignedInt(in.get()),
                Byte.toUnsignedInt(in.get()),
                in.getShort(),
                in.getShort());
    }

    private static void requireUnsigned(int value, int bits, String name) {
        int max = (1 << bits) - 1;
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(name + " is outside u" + bits + ": " + value);
        }
    }
}
