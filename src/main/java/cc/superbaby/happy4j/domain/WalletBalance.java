package cc.superbaby.happy4j.domain;

import java.time.Instant;

/**
 * Server-authoritative currencies returned by DDZ3DQueryInfo over Lotus.
 */
public record WalletBalance(long beans, long diamonds, long goldCoins, Instant refreshedAt) {
    public WalletBalance {
        refreshedAt = refreshedAt == null ? Instant.EPOCH : refreshedAt;
    }

    public static WalletBalance empty() {
        return new WalletBalance(0, 0, 0, Instant.EPOCH);
    }
}
