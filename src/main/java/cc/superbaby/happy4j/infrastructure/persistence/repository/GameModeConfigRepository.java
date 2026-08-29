package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.GameModeConfigEntity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameModeConfigRepository extends JpaRepository<GameModeConfigEntity, Integer> {
    List<GameModeConfigEntity> findAllByOrderBySceneId();
}
