package cc.superbaby.happy4j.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_event")
public class GameEventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private GameClientSessionEntity session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUserEntity user;
    @Column(name = "event_index", nullable = false)
    private long eventIndex;
    @Column(nullable = false, length = 48)
    private String kind;
    @Column(nullable = false, length = 1024)
    private String message;
    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected GameEventEntity() {
    }

    public GameEventEntity(GameClientSessionEntity session, AppUserEntity user, long eventIndex,
                           String kind, String message, String payloadJson, Instant occurredAt) {
        this.session = session;
        this.user = user;
        this.eventIndex = eventIndex;
        this.kind = kind;
        this.message = message;
        this.payloadJson = payloadJson;
        this.occurredAt = occurredAt;
    }
}
