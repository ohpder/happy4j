package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.AiConfigurationEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiConfigurationRepository extends JpaRepository<AiConfigurationEntity, UUID> {
}
