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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "game_round_record",
        uniqueConstraints = @UniqueConstraint(name = "uk_game_round_record_key", columnNames = {"session_id", "round_key"}))
public class GameRoundRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private AppUserEntity user;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id")
    private GameClientSessionEntity session;
    @Column(name = "round_key", nullable = false, length = 128)
    private String roundKey;
    @Column(name = "game_uin", nullable = false)
    private long gameUin;
    @Column(name = "scene_id", nullable = false)
    private int sceneId;
    @Column(name = "room_id", nullable = false)
    private int roomId;
    @Column(name = "table_id", nullable = false)
    private int tableId;
    @Column(name = "local_seat_id", nullable = false)
    private int localSeatId;
    @Column(name = "landlord_seat_id", nullable = false)
    private int landlordSeatId;
    @Column(nullable = false)
    private int multiplier;
    @Column(name = "bean_change", nullable = false)
    private long beanChange;
    @Column(name = "beans_after", nullable = false)
    private long beansAfter;
    @Column(nullable = false)
    private long factor;
    @Column(nullable = false, length = 16)
    private String result;
    @Column(name = "detail_json", nullable = false, columnDefinition = "LONGTEXT")
    private String detailJson;
    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    protected GameRoundRecordEntity() {
    }

    public GameRoundRecordEntity(AppUserEntity user, GameClientSessionEntity session, String roundKey,
                                 long gameUin, int sceneId, int roomId, int tableId, int localSeatId,
                                 int landlordSeatId, int multiplier, long beanChange, long beansAfter,
                                 long factor, String result, String detailJson, Instant finishedAt) {
        this.user = user;
        this.session = session;
        this.roundKey = roundKey;
        this.gameUin = gameUin;
        this.sceneId = sceneId;
        this.roomId = roomId;
        this.tableId = tableId;
        this.localSeatId = localSeatId;
        this.landlordSeatId = landlordSeatId;
        this.multiplier = multiplier;
        this.beanChange = beanChange;
        this.beansAfter = beansAfter;
        this.factor = factor;
        this.result = result;
        this.detailJson = detailJson;
        this.finishedAt = finishedAt;
    }

    public Long getId() {
        return id;
    }

    public UUID sessionId() {
        return session.getId();
    }

    public long getGameUin() {
        return gameUin;
    }

    public int getSceneId() {
        return sceneId;
    }

    public int getRoomId() {
        return roomId;
    }

    public int getTableId() {
        return tableId;
    }

    public int getLocalSeatId() {
        return localSeatId;
    }

    public int getLandlordSeatId() {
        return landlordSeatId;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public long getBeanChange() {
        return beanChange;
    }

    public long getBeansAfter() {
        return beansAfter;
    }

    public long getFactor() {
        return factor;
    }

    public String getResult() {
        return result;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
