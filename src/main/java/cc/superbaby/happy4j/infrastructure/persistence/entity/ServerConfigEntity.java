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
@Table(name = "server_config")
public class ServerConfigEntity {
    @Id
    @Column(name = "config_key", length = 128)
    private String key;
    @Column(name = "value_json", nullable = false, columnDefinition = "LONGTEXT")
    private String valueJson;
    @Column(nullable = false, length = 512)
    private String description;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private long version;

    protected ServerConfigEntity() {
    }

    public ServerConfigEntity(String key, String valueJson, String description) {
        this.key = key;
        this.valueJson = valueJson;
        this.description = description;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public String getKey() {
        return key;
    }

    public String getValueJson() {
        return valueJson;
    }

    public String getDescription() {
        return description;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void update(String valueJson, String description) {
        this.valueJson = valueJson;
        this.description = description;
    }
}
