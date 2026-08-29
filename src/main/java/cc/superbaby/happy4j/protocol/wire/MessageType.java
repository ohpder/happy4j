package cc.superbaby.happy4j.protocol.wire;

import java.util.Arrays;

/**
 * Values recovered from TSDK4CSharp.ENM_SVRMSGTYPE.
 */
public enum MessageType {
    REQUEST(0),
    RESPONSE(1),
    NOTIFY(2),
    ACK(3),
    INTERNAL(4);

    private final int wireValue;

    MessageType(int wireValue) {
        this.wireValue = wireValue;
    }

    public int wireValue() {
        return wireValue;
    }

    public static MessageType fromWireValue(int value) {
        return Arrays.stream(values())
                .filter(type -> type.wireValue == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown message type: " + value));
    }
}
