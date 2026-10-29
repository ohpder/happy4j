package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.GameRoundRecordEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GameRoundRecordRepository extends JpaRepository<GameRoundRecordEntity, Long> {
    @Query("select count(r) > 0 from GameRoundRecordEntity r "
            + "where r.session.id = :sessionId and r.roundKey = :roundKey")
    boolean existsForRound(@Param("sessionId") UUID sessionId, @Param("roundKey") String roundKey);

    List<GameRoundRecordEntity> findByUser_IdOrderByFinishedAtDesc(UUID userId);

    List<GameRoundRecordEntity> findByUser_IdAndSession_IdOrderByFinishedAtDesc(UUID userId, UUID sessionId);
}
