package cc.superbaby.happy4j.infrastructure.persistence.entity;

import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_login_state")
public class GameLoginStateEntity {
    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUserEntity user;
    @Column(nullable = false, length = 16)
    private String provider;
    @Column(name = "game_uin", nullable = false)
    private long gameUin;
    @Lob
    @Column(name = "encrypted_payload", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] encryptedPayload;
    @Column(nullable = false, columnDefinition = "BINARY(12)")
    private byte[] iv;
    @Column(name = "key_version", nullable = false)
    private int keyVersion = 1;
    @Column(nullable = false, length = 24)
    private String status = "ACTIVE";
    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;
    @Version
    private long version;

    protected GameLoginStateEntity() {
    }

    public GameLoginStateEntity(UUID id, AppUserEntity user, String provider, long gameUin, byte[] encryptedPayload, byte[] iv, Instant expiresAt) {
        this.id = id;
        this.user = user;
        this.provider = provider;
        this.gameUin = gameUin;
        this.encryptedPayload = encryptedPayload;
        this.iv = iv;
        this.issuedAt = this.lastUsedAt = Instant.now();
        this.expiresAt = expiresAt;
    }

    public UUID getId() {
        return id;
    }

    public AppUserEntity getUser() {
        return user;
    }

    public String getProvider() {
        return provider;
    }

    public long getGameUin() {
        return gameUin;
    }

    public byte[] getEncryptedPayload() {
        return encryptedPayload.clone();
    }

    public byte[] getIv() {
        return iv.clone();
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getStatus() {
        return status;
    }

    public void used() {
        lastUsedAt = Instant.now();
    }

    public void invalidate() {
        status = "INVALID";
    }
}
