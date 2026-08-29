package cc.superbaby.happy4j.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_client_session")
public class GameClientSessionEntity {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUserEntity user;
    @Column(nullable = false, length = 32)
    private String phase;
    @Column(nullable = false, length = 512)
    private String detail;
    @Column(length = 16)
    private String provider;
    @Column(name = "game_uin")
    private Long gameUin;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "LONGTEXT")
    private String snapshotJson;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Column(name = "disconnected_at")
    private Instant disconnectedAt;
    @Version
    private long version;

    protected GameClientSessionEntity() {
    }

    public GameClientSessionEntity(UUID id, AppUserEntity user, String phase, String detail, String snapshotJson) {
        this.id = id;
        this.user = user;
        this.phase = phase;
        this.detail = detail;
        this.snapshotJson = snapshotJson;
    }

    @PrePersist
    void create() {
        Instant now = Instant.now();
        createdAt = updatedAt = now;
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public AppUserEntity getUser() {
        return user;
    }

    public void synchronize(String phase, String detail, String provider, Long gameUin, String snapshotJson) {
        this.phase = phase;
        this.detail = detail;
        this.provider = provider;
        this.gameUin = gameUin;
        this.snapshotJson = snapshotJson;
        if ("DISCONNECTED".equals(phase) || "FAILED".equals(phase)) disconnectedAt = Instant.now();
    }
}
