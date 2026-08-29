package cc.superbaby.happy4j.web;

import cc.superbaby.happy4j.application.HappyDdzClientService;
import cc.superbaby.happy4j.application.RemoteMissionCatalogService;
import cc.superbaby.happy4j.application.ServerConfigService;
import cc.superbaby.happy4j.domain.AppPrincipal;
import cc.superbaby.happy4j.infrastructure.persistence.entity.GameModeConfigEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameModeConfigRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.MissionDefinitionRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.ServerConfigRepository;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/admin")
public class AdminConfigController {
    private final ServerConfigRepository configs;
    private final ServerConfigService configService;
    private final MissionDefinitionRepository missions;
    private final RemoteMissionCatalogService remote;
    private final HappyDdzClientService clients;
    private final GameModeConfigRepository modes;

    public AdminConfigController(ServerConfigRepository configs,
                                 ServerConfigService configService,
                                 MissionDefinitionRepository missions,
                                 RemoteMissionCatalogService remote,
                                 HappyDdzClientService clients,
                                 GameModeConfigRepository modes) {
        this.configs = configs;
        this.configService = configService;
        this.missions = missions;
        this.remote = remote;
        this.clients = clients;
        this.modes = modes;
    }

    @GetMapping("/config")
    public List<ConfigView> configs(@AuthenticationPrincipal AppPrincipal user) {
        requireAdmin(user);
        return configs.findAll().stream()
                .map(entity -> new ConfigView(entity.getKey(), entity.getValueJson(),
                        entity.getDescription(), entity.getUpdatedAt()))
                .toList();
    }

    @PutMapping("/config/{key}")
    public ConfigView put(@AuthenticationPrincipal AppPrincipal user,
                          @PathVariable String key,
                          @Valid @RequestBody ConfigRequest request) {
        requireAdmin(user);
        configService.put(key, request.value(), request.description());
        return configs.findById(key)
                .map(entity -> new ConfigView(entity.getKey(), entity.getValueJson(),
                        entity.getDescription(), entity.getUpdatedAt()))
                .orElseThrow();
    }

    @GetMapping("/missions")
    public List<MissionView> missions(@AuthenticationPrincipal AppPrincipal user) {
        requireAdmin(user);
        return missions.findByActiveTrueOrderByMissionId().stream()
                .map(entity -> new MissionView(entity.getMissionId(), entity.getTitle(),
                        entity.getDescription(), entity.getSource(), entity.getLastSyncedAt()))
                .toList();
    }

    @PostMapping("/missions/sync/{sessionId}")
    public CompletableFuture<Integer> sync(@AuthenticationPrincipal AppPrincipal user,
                                           @PathVariable UUID sessionId) {
        requireAdmin(user);
        clients.assertOwner(user.userId(), sessionId);
        return remote.synchronize(sessionId, true);
    }

    @GetMapping("/modes")
    public List<ModeView> modes(@AuthenticationPrincipal AppPrincipal user) {
        requireAdmin(user);
        return modes.findAllByOrderBySceneId().stream().map(AdminConfigController::view).toList();
    }

    @GetMapping("/runtime")
    public HappyDdzClientService.RuntimeCapacity runtime(@AuthenticationPrincipal AppPrincipal user) {
        requireAdmin(user);
        return clients.runtimeCapacity();
    }

    @PutMapping("/modes/{sceneId}")
    public ModeView mode(@AuthenticationPrincipal AppPrincipal user,
                         @PathVariable int sceneId,
                         @Valid @RequestBody ModeRequest request) {
        requireAdmin(user);
        GameModeConfigEntity entity = modes.findById(sceneId).orElseGet(() ->
                new GameModeConfigEntity(sceneId, request.family(), request.name(), request.minBeans(),
                        request.maxBeans(), request.cap(), request.baseScore(), request.enabled(), "ADMIN"));
        entity.update(request.family(), request.name(), request.minBeans(), request.maxBeans(), request.cap(),
                request.baseScore(), request.enabled(), "ADMIN");
        return view(modes.save(entity));
    }

    private static void requireAdmin(AppPrincipal user) {
        if (user == null || !user.admin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "需要管理员账号");
        }
    }

    private static ModeView view(GameModeConfigEntity entity) {
        return new ModeView(entity.getSceneId(), entity.getFamily(), entity.getName(), entity.getMinBeans(),
                entity.getMaxBeans(), entity.getCap(), entity.getBaseScore(), entity.isEnabled(), entity.getSource());
    }

    public record ConfigRequest(JsonNode value, String description) {
    }

    public record ConfigView(String key, String valueJson, String description, Instant updatedAt) {
    }

    public record MissionView(int missionId, String title, String description, String source,
                              Instant lastSyncedAt) {
    }

    public record ModeRequest(String family, String name, long minBeans, long maxBeans, long cap,
                              int baseScore, boolean enabled) {
    }

    public record ModeView(int sceneId, String family, String name, long minBeans, long maxBeans,
                           long cap,
                           int baseScore, boolean enabled, String source) {
    }
}
