package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.protocol.mission.MissionStatusProtocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.MissingNode;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
public class RemoteMissionCatalogService {
    private static final Logger LOG = LoggerFactory.getLogger(RemoteMissionCatalogService.class);
    private static final int DEFAULT_BEGIN = 1;
    private static final int DEFAULT_END = 32_767;
    private static final int DEFAULT_TTL_SECONDS = 900;

    private final ClientRuntimeRegistry runtime;
    private final MissionDefinitionService definitions;
    private final ServerConfigService config;
    private final MissionStatusProtocol protocol;
    private final ObjectMapper json;

    public RemoteMissionCatalogService(ClientRuntimeRegistry runtime,
                                       MissionDefinitionService definitions,
                                       ServerConfigService config,
                                       MissionStatusProtocol protocol,
                                       ObjectMapper json) {
        this.runtime = runtime;
        this.definitions = definitions;
        this.config = config;
        this.protocol = protocol;
        this.json = json;
    }

    public CompletableFuture<Integer> synchronize(UUID sessionId) {
        return synchronize(sessionId, false);
    }

    public CompletableFuture<Integer> synchronize(UUID sessionId, boolean force) {
        int ttl = config.integer(ServerConfigKeys.MISSION_CATALOG_TTL_SECONDS, DEFAULT_TTL_SECONDS);
        if (!force && definitions.fresh(Duration.ofSeconds(Math.max(10, ttl)))) {
            return CompletableFuture.completedFuture(0);
        }

        var range = config.get(ServerConfigKeys.MISSION_SYNC_RANGE);
        int begin = range == null ? DEFAULT_BEGIN : range.path("begin").asInt(DEFAULT_BEGIN);
        int end = range == null ? DEFAULT_END : range.path("end").asInt(DEFAULT_END);
        List<Integer> missionIds = definitions.ids().stream()
                .filter(id -> id >= begin && id <= end)
                .limit(400)
                .toList();
        if (missionIds.isEmpty()) return CompletableFuture.completedFuture(0);

        return fetchBatch(sessionId, runtime.get(sessionId).gameCredential(), missionIds);
    }

    /**
     * MissionSvr rejects a whole activity list when it contains one stale mission ID.
     */
    private CompletableFuture<Integer> fetchBatch(UUID sessionId,
                                                  GameCredential credential,
                                                  List<Integer> missionIds) {
        return runtime.connection(sessionId)
                .request(protocol.fetchKnown(credential, missionIds), Duration.ofSeconds(12))
                .thenCompose(response -> {
                    try {
                        var root = json.readTree(response.json());
                        LOG.debug("MissionSvr 7304 response={}", root);
                        if (root.hasNonNull("decodeError")) {
                            throw new IllegalStateException("MissionSvr 7304 解码失败: "
                                    + root.path("decodeError").asString());
                        }
                        int result = field(root, "ResultID", "resultId", "result_id").asInt();
                        if (result != 0) {
                            if (missionIds.size() == 1) {
                                LOG.debug("MissionSvr ignored stale mission id={} result={}", missionIds.get(0), result);
                                return CompletableFuture.completedFuture(0);
                            }
                            int middle = missionIds.size() / 2;
                            return fetchBatch(sessionId, credential, missionIds.subList(0, middle))
                                    .thenCompose(first -> fetchBatch(sessionId, credential,
                                            missionIds.subList(middle, missionIds.size()))
                                            .thenApply(second -> first + second));
                        }
                        var values = field(root, "MissionStatus", "missionStatus", "mission_status");
                        if (!values.isArray()) return CompletableFuture.completedFuture(0);
                        values.forEach(value -> definitions.synchronize(value, "MISSION_SVR_7304"));
                        return CompletableFuture.completedFuture(values.size());
                    } catch (Exception failure) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("在线任务目录解析失败", failure));
                    }
                });
    }

    private static JsonNode field(JsonNode root, String... names) {
        for (String name : names) {
            var value = root.get(name);
            if (value != null) return value;
        }
        return MissingNode.getInstance();
    }
}
