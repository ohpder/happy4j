package cc.superbaby.happy4j.infrastructure.config;

import cc.superbaby.happy4j.application.port.GameModeCatalogPort;
import cc.superbaby.happy4j.domain.GameMode;
import cc.superbaby.happy4j.infrastructure.persistence.entity.GameModeConfigEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameModeConfigRepository;
import jakarta.annotation.PostConstruct;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DatabaseGameModeCatalog implements GameModeCatalogPort {
    private final GameModeConfigRepository repository;

    public DatabaseGameModeCatalog(GameModeConfigRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void seedFromApkWhenEmpty() {
        if (repository.count() != 0) return;
        repository.saveAll(new XmlGameModeCatalog().modes().stream()
                .map(mode -> new GameModeConfigEntity(
                        mode.sceneId(), mode.family(), mode.name(), mode.minBeans(), mode.maxBeans(),
                        mode.cap(), mode.baseScore(), mode.enabled(), "APK_XML_SEED"))
                .toList());
    }

    @Override
    public List<GameMode> modes() {
        return repository.findAllByOrderBySceneId().stream()
                .map(entity -> new GameMode(
                        entity.getSceneId(), entity.getFamily(), entity.getName(), entity.getMinBeans(),
                        Math.max(0, entity.getMaxBeans()), entity.getCap(), entity.getBaseScore(), entity.isEnabled()))
                .toList();
    }
}
