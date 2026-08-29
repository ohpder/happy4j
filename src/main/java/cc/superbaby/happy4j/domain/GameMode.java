package cc.superbaby.happy4j.domain;

public record GameMode(
        int sceneId,
        String family,
        String name,
        long minBeans,
        long maxBeans,
        long cap,
        int baseScore,
        boolean enabled) {
}
