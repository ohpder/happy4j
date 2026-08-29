package cc.superbaby.happy4j.protocol.wire;

public record CMsgFrame(CcsHeader transportHeader, CMsgHeader messageHeader, byte[] payload) {
    public CMsgFrame {
        if (transportHeader == null || messageHeader == null) {
            throw new IllegalArgumentException("headers are required");
        }
        payload = payload == null ? new byte[0] : payload.clone();
    }

    @Override
    public byte[] payload() {
        return payload.clone();
    }
}
