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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_account", uniqueConstraints = @UniqueConstraint(name = "uk_game_account_identity", columnNames = {"provider", "game_uin"}))
public class GameAccountEntity {
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
    @Column(nullable = false, length = 128)
    private String nickname = "欢乐玩家";
    @Column(name = "avatar_url", nullable = false, length = 1024)
    private String avatarUrl = "";
    @Column(nullable = false)
    private long beans;
    @Column(nullable = false)
    private long diamonds;
    @Column(name = "gold_coins", nullable = false)
    private long goldCoins;
    @Column(name = "last_login_at", nullable = false)
    private Instant lastLoginAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected GameAccountEntity() {
    }

    public GameAccountEntity(UUID id, AppUserEntity user, String provider, long gameUin) {
        this.id = id;
        this.user = user;
        this.provider = provider;
        this.gameUin = gameUin;
    }

    @PrePersist
    void create() {
        Instant now = Instant.now();
        lastLoginAt = updatedAt = now;
    }

    @PreUpdate
    void update() {
        updatedAt = Instant.now();
    }

    public void synchronize(String nickname, String avatarUrl, long beans, long diamonds, long goldCoins) {
        this.nickname = nickname == null ? "欢乐玩家" : nickname;
        this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        this.beans = beans;
        this.diamonds = diamonds;
        this.goldCoins = goldCoins;
        this.lastLoginAt = Instant.now();
    }
}
