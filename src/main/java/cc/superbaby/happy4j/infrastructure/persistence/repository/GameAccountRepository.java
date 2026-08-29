package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.GameAccountEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GameAccountRepository extends JpaRepository<GameAccountEntity, UUID> {
    Optional<GameAccountEntity> findByProviderAndGameUin(String provider, long gameUin);
}
