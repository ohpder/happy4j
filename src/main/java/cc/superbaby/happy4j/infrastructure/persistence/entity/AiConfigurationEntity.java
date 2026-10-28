package cc.superbaby.happy4j.infrastructure.persistence.entity;

import cc.superbaby.happy4j.domain.AiConfiguration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_configuration")
public class AiConfigurationEntity {
    @Id
    @Column(name = "user_id", columnDefinition = "BINARY(16)")
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID userId;
    @Column(name = "scene_id", nullable = false)
    private int sceneId;
    @Column(name = "hosting_mode", nullable = false, length = 16)
    private String hostingMode = "BROWSER";
    @Column(name = "auto_change_table", nullable = false)
    private boolean autoChangeTable = true;
    @Column(name = "auto_claim_beans", nullable = false)
    private boolean autoClaimBeans = true;
    @Column(name = "stop_at_beans", nullable = false)
    private long stopAtBeans;
    @Column(name = "stop_below_beans", nullable = false)
    private long stopBelowBeans;
    @Column(name = "inference_engine", nullable = false, length = 16)
    private String inferenceEngine = "DOUZERO";
    @Column(name = "api_base_url", nullable = false, length = 1024)
    private String apiBaseUrl = "";
    @Column(name = "api_model", nullable = false, length = 128)
    private String apiModel = "";
    @Lob
    @Column(name = "encrypted_api_key", columnDefinition = "LONGBLOB")
    private byte[] encryptedApiKey;
    @Column(name = "api_key_iv", columnDefinition = "BINARY(12)")
    private byte[] apiKeyIv;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected AiConfigurationEntity() {
    }

    public AiConfigurationEntity(UUID userId) {
        this.userId = userId;
    }

    public void apply(int sceneId, String hostingMode, boolean autoChangeTable, boolean autoClaimBeans,
                      long stopAtBeans, long stopBelowBeans, String inferenceEngine,
                      String apiBaseUrl, String apiModel, boolean enabled) {
        this.sceneId = sceneId;
        this.hostingMode = hostingMode;
        this.autoChangeTable = autoChangeTable;
        this.autoClaimBeans = autoClaimBeans;
        this.stopAtBeans = stopAtBeans;
        this.stopBelowBeans = stopBelowBeans;
        this.inferenceEngine = inferenceEngine;
        this.apiBaseUrl = apiBaseUrl;
        this.apiModel = apiModel;
        this.enabled = enabled;
    }

    public void apiKey(byte[] encrypted, byte[] iv) {
        encryptedApiKey = encrypted == null ? null : encrypted.clone();
        apiKeyIv = iv == null ? null : iv.clone();
    }

    public boolean hasApiKey() {
        return encryptedApiKey != null && encryptedApiKey.length > 0 && apiKeyIv != null && apiKeyIv.length == 12;
    }

    public byte[] getEncryptedApiKey() {
        return encryptedApiKey == null ? null : encryptedApiKey.clone();
    }

    public byte[] getApiKeyIv() {
        return apiKeyIv == null ? null : apiKeyIv.clone();
    }

    public String getInferenceEngine() {
        return inferenceEngine;
    }

    public UUID getUserId() {
        return userId;
    }

    public void enabled(boolean value) {
        enabled = value;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public String getApiModel() {
        return apiModel;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public AiConfiguration toDomain() {
        return new AiConfiguration(sceneId, hostingMode, autoChangeTable, autoClaimBeans,
                stopAtBeans, stopBelowBeans, inferenceEngine, apiBaseUrl, apiModel,
                hasApiKey(), enabled, updatedAt);
    }
}
