package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.MissionDefinitionEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MissionDefinitionRepository extends JpaRepository<MissionDefinitionEntity, Integer> {
    List<MissionDefinitionEntity> findByActiveTrueOrderByMissionId();

    Optional<MissionDefinitionEntity> findTopByActiveTrueOrderByLastSyncedAtDesc();
}
