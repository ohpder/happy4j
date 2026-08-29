package cc.superbaby.happy4j.domain;

import java.time.Instant;

public record RemoteEvent(long index, String kind, String message, Object data,
                          Instant occurredAt) {
}
