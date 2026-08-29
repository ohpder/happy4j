package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.GameClientSessionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GameClientSessionRepository extends JpaRepository<GameClientSessionEntity, UUID> {
    Optional<GameClientSessionEntity> findByIdAndUserId(UUID id, UUID userId);

    List<GameClientSessionEntity> findTop20ByUserIdOrderByUpdatedAtDesc(UUID userId);
}
