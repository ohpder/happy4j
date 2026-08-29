package cc.superbaby.happy4j.websocket;

import java.time.Instant;

public record WebSocketEnvelope(String type, String requestId, Object payload, Instant occurredAt) {
    public static WebSocketEnvelope of(String type, String requestId, Object payload) {
        return new WebSocketEnvelope(type, requestId, payload, Instant.now());
    }
}
