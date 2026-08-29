package cc.superbaby.happy4j.domain;

import java.time.Instant;

public record ActivityView(String id, String title, int messageId, String status, String payload,
                           Instant updatedAt) {
}
