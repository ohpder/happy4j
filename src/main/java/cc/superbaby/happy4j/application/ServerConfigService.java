package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.infrastructure.persistence.entity.ServerConfigEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.ServerConfigRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
public class ServerConfigService {
    private final ServerConfigRepository repository;
    private final ObjectMapper json;

    public ServerConfigService(ServerConfigRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public JsonNode get(String key) {
        return repository.findById(key).map(value -> {
            try {
                return json.readTree(value.getValueJson());
            } catch (Exception failure) {
                throw new IllegalStateException("配置损坏: " + key, failure);
            }
        }).orElse(null);
    }

    public int integer(String key, int fallback) {
        JsonNode value = get(key);
        return value == null ? fallback : value.asInt(fallback);
    }

    public boolean bool(String key, boolean fallback) {
        JsonNode value = get(key);
        return value == null ? fallback : value.asBoolean(fallback);
    }

    public List<Integer> integers(String key) {
        JsonNode value = get(key);
        if (value == null || !value.isArray()) return List.of();
        List<Integer> result = new ArrayList<>();
        value.forEach(item -> result.add(item.asInt()));
        return List.copyOf(result);
    }

    @Transactional
    public void put(String key, Object value, String description) {
        String encoded;
        try {
            encoded = json.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalArgumentException("配置不可序列化", failure);
        }
        ServerConfigEntity entity = repository.findById(key)
                .orElseGet(() -> new ServerConfigEntity(key, encoded, description));
        entity.update(encoded, description == null ? "" : description);
        repository.save(entity);
    }
}
