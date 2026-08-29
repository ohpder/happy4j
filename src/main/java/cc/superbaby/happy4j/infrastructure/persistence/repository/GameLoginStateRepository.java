package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.GameLoginStateEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameLoginStateRepository extends JpaRepository<GameLoginStateEntity, UUID> {
    Optional<GameLoginStateEntity> findFirstByUserIdAndStatusAndExpiresAtAfterOrderByLastUsedAtDesc(
            UUID userId, String status, Instant now);

    List<GameLoginStateEntity> findByUserIdAndStatus(UUID userId, String status);
}
