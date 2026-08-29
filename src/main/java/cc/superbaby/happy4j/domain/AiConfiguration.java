package cc.superbaby.happy4j.domain;

import java.time.Instant;

/** Per-site-user settings for the browser-side DouZero HappyDouDiZhu automation loop. */
public record AiConfiguration(
        int sceneId,
        boolean autoChangeTable,
        boolean autoClaimBeans,
        long stopAtBeans,
        long stopBelowBeans,
        String inferenceEngine,
        String apiBaseUrl,
        String apiModel,
        boolean apiKeyConfigured,
        boolean enabled,
        Instant updatedAt) {
    public AiConfiguration {
        sceneId = Math.max(0, sceneId);
        stopAtBeans = Math.max(0, stopAtBeans);
        stopBelowBeans = Math.max(0, stopBelowBeans);
        inferenceEngine = inferenceEngine == null || inferenceEngine.isBlank() ? "DOUZERO" : inferenceEngine;
        apiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl;
        apiModel = apiModel == null ? "" : apiModel;
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public static AiConfiguration defaults() {
        return new AiConfiguration(0, true, true, 0, 0,
                "DOUZERO", "", "", false, false, Instant.EPOCH);
    }
}
