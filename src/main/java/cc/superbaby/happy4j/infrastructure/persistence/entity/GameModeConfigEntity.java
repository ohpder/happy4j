package cc.superbaby.happy4j.infrastructure.persistence.entity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "game_mode_config")
public class GameModeConfigEntity {
    @Id
    @Column(name = "scene_id")
    private int sceneId;
    @Column(nullable = false, length = 32)
    private String family;
    @Column(nullable = false, length = 128)
    private String name;
    @Column(name = "min_beans", nullable = false)
    private long minBeans;
    @Column(name = "max_beans", nullable = false)
    private long maxBeans;
    @Column(nullable = false)
    private long cap;
    @Column(name = "base_score", nullable = false)
    private int baseScore;
    @Column(nullable = false)
    private boolean enabled;
    @Column(nullable = false, length = 32)
    private String source;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected GameModeConfigEntity() {
    }

    public GameModeConfigEntity(int sceneId, String family, String name, long minBeans, long maxBeans, long cap, int baseScore, boolean enabled, String source) {
        this.sceneId = sceneId;
        update(family, name, minBeans, maxBeans, cap, baseScore, enabled, source);
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void update(String family, String name, long minBeans, long maxBeans, long cap, int baseScore, boolean enabled, String source) {
        this.family = family;
        this.name = name;
        this.minBeans = minBeans;
        this.maxBeans = Math.max(0, maxBeans);
        this.cap = cap;
        this.baseScore = baseScore;
        this.enabled = enabled;
        this.source = source;
    }

    public int getSceneId() {
        return sceneId;
    }

    public String getFamily() {
        return family;
    }

    public String getName() {
        return name;
    }

    public long getMinBeans() {
        return minBeans;
    }

    public long getMaxBeans() {
        return maxBeans;
    }

    public long getCap() {
        return cap;
    }

    public int getBaseScore() {
        return baseScore;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSource() {
        return source;
    }
}
