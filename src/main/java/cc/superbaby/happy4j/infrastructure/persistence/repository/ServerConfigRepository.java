package cc.superbaby.happy4j.infrastructure.persistence.repository;

import cc.superbaby.happy4j.infrastructure.persistence.entity.ServerConfigEntity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ServerConfigRepository extends JpaRepository<ServerConfigEntity, String> {
}
