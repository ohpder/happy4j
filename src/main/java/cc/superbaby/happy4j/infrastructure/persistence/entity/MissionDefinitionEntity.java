package cc.superbaby.happy4j.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;

@Entity
@Table(name = "mission_definition")
public class MissionDefinitionEntity {
    @Id
    @Column(name = "mission_id")
    private int missionId;
    @Column(nullable = false, length = 160)
    private String title;
    @Column(nullable = false, length = 1024)
    private String description = "";
    @Column(nullable = false, length = 32)
    private String source;
    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;
    @Column(nullable = false)
    private boolean active = true;
    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;
    @Version
    private long version;

    protected MissionDefinitionEntity() {
    }

    public MissionDefinitionEntity(int missionId, String title, String source) {
        this.missionId = missionId;
        this.title = title;
        this.source = source;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        lastSyncedAt = Instant.now();
    }

    public int getMissionId() {
        return missionId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getSource() {
        return source;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void synchronize(String title, String description, String source, String payloadJson) {
        if (title != null && !title.isBlank()) this.title = title;
        if (description != null) this.description = description;
        this.source = source;
        this.payloadJson = payloadJson;
        this.active = true;
    }
}
