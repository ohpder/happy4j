package cc.superbaby.happy4j.domain;

import java.time.Instant;

/** Per-site-user settings for browser or server AI automation. */
public record AiConfiguration(
        int sceneId,
        String hostingMode,
        boolean autoChangeTable,
        boolean autoClaimBeans,
        long stopAtBeans,
        long stopBelowBeans,
        String inferenceEngine,
        String apiBaseUrl,
        String apiModel,
        boolean apiKeyConfigured,
        boolean enabled,
        boolean stopAfterRound,
        Instant updatedAt) {
    public AiConfiguration {
        sceneId = Math.max(0, sceneId);
        hostingMode = hostingMode == null || hostingMode.isBlank() ? "BROWSER" : hostingMode;
        stopAtBeans = Math.max(0, stopAtBeans);
        stopBelowBeans = Math.max(0, stopBelowBeans);
        inferenceEngine = inferenceEngine == null || inferenceEngine.isBlank() ? "DOUZERO" : inferenceEngine;
        apiBaseUrl = apiBaseUrl == null ? "" : apiBaseUrl;
        apiModel = apiModel == null ? "" : apiModel;
        stopAfterRound = enabled && stopAfterRound;
        updatedAt = updatedAt == null ? Instant.EPOCH : updatedAt;
    }

    public AiConfiguration(int sceneId, boolean autoChangeTable, boolean autoClaimBeans,
                           long stopAtBeans, long stopBelowBeans, String inferenceEngine,
                           String apiBaseUrl, String apiModel, boolean apiKeyConfigured,
                           boolean enabled, Instant updatedAt) {
        this(sceneId, "BROWSER", autoChangeTable, autoClaimBeans, stopAtBeans, stopBelowBeans,
                inferenceEngine, apiBaseUrl, apiModel, apiKeyConfigured, enabled, false, updatedAt);
    }

    public AiConfiguration(int sceneId, String hostingMode, boolean autoChangeTable, boolean autoClaimBeans,
                           long stopAtBeans, long stopBelowBeans, String inferenceEngine,
                           String apiBaseUrl, String apiModel, boolean apiKeyConfigured,
                           boolean enabled, Instant updatedAt) {
        this(sceneId, hostingMode, autoChangeTable, autoClaimBeans, stopAtBeans, stopBelowBeans,
                inferenceEngine, apiBaseUrl, apiModel, apiKeyConfigured, enabled, false, updatedAt);
    }

    public static AiConfiguration defaults() {
        return new AiConfiguration(0, "BROWSER", true, true, 0, 0,
                "DOUZERO", "", "", false, false, false, Instant.EPOCH);
    }
}
