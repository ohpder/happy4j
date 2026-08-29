package cc.superbaby.happy4j.domain;

import java.time.Instant;

/** Player-owned consumable reported by the item service. */
public record InventoryItem(int itemId, String name, long count, Instant refreshedAt) {
    public InventoryItem {
        if (itemId <= 0) throw new IllegalArgumentException("itemId must be positive");
        name = name == null || name.isBlank() ? "道具 " + itemId : name;
        count = Math.max(0, count);
        refreshedAt = refreshedAt == null ? Instant.now() : refreshedAt;
    }
}
