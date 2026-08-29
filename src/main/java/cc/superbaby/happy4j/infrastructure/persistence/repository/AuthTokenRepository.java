package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.AuthTokenEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository extends JpaRepository<AuthTokenEntity, UUID> {
    Optional<AuthTokenEntity> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    List<AuthTokenEntity> findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(UUID userId);

    @Modifying
    @Query("delete from AuthTokenEntity t where t.expiresAt < :now or t.revokedAt is not null")
    int deleteExpiredOrRevoked(Instant now);
}
