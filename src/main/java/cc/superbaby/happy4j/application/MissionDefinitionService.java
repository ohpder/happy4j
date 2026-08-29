package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.application.port.MissionCatalogPort;
import cc.superbaby.happy4j.infrastructure.persistence.entity.MissionDefinitionEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.MissionDefinitionRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
public class MissionDefinitionService implements MissionCatalogPort {
    private final MissionDefinitionRepository repository;
    private final ServerConfigService config;
    private final MissionLabelCatalog labels;

    public MissionDefinitionService(MissionDefinitionRepository repository, ServerConfigService config,
                                    MissionLabelCatalog labels) {
        this.repository = repository;
        this.config = config;
        this.labels = labels;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Integer> ids() {
        List<Integer> online = repository.findByActiveTrueOrderByMissionId().stream()
                .map(MissionDefinitionEntity::getMissionId)
                .toList();
        return online.isEmpty() ? config.integers(ServerConfigKeys.MISSION_SEED_IDS) : online;
    }

    @Override
    @Transactional(readOnly = true)
    public String title(int id) {
        String stored = repository.findById(id).map(MissionDefinitionEntity::getTitle).orElse("");
        if (!stored.isBlank() && !stored.equals("任务 " + id)) return stored;
        String catalogTitle = labels.title(id);
        return catalogTitle.isBlank() ? "任务 " + id : catalogTitle;
    }

    @Transactional
    public void synchronize(JsonNode value, String source) {
        int id = integer(value, "MissionID", "missionId", "mission_id");
        if (id <= 0) return;
        String remoteTitle = text(value, "MissionDesc", "missionDesc", "Title", "title", "Name", "name");
        String title = remoteTitle.isBlank() ? labels.title(id) : remoteTitle;
        MissionDefinitionEntity entity = repository.findById(id).orElseGet(() ->
                new MissionDefinitionEntity(id, title.isBlank() ? "任务 " + id : title, source));
        entity.synchronize(title, text(value, "Description", "description"), source, value.toString());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public boolean fresh(Duration ttl) {
        return repository.findTopByActiveTrueOrderByLastSyncedAtDesc()
                .map(value -> value.getLastSyncedAt().plus(ttl).isAfter(Instant.now()))
                .orElse(false);
    }

    private static int integer(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null) return value.asInt();
        }
        return 0;
    }

    private static String text(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.asString().isBlank()) return value.asString();
        }
        return "";
    }
}
