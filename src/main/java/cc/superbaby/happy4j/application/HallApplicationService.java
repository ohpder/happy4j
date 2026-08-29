package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.application.ProtocolCallCatalog.NamedCall;
import cc.superbaby.happy4j.application.port.GameTransportPort;
import cc.superbaby.happy4j.domain.ActivityView;
import cc.superbaby.happy4j.domain.ClientSession;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.MissionTask;
import cc.superbaby.happy4j.domain.InventoryItem;
import cc.superbaby.happy4j.domain.PlayerProfile;
import cc.superbaby.happy4j.domain.WalletBalance;
import cc.superbaby.happy4j.protocol.cgi.HallCgiProtocol;
import cc.superbaby.happy4j.protocol.grpc.DdzWalletGrpcCodec;
import cc.superbaby.happy4j.config.Happy4jProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Hall-level use cases, isolated from room entry and table state transitions.
 */
@Service
public class HallApplicationService {
    private static final Logger LOG = LoggerFactory.getLogger(HallApplicationService.class);
    public static final int SUPER_DOUBLE_ITEM_ID = 30020001;
    private final ClientRuntimeRegistry runtime;
    private final ProtocolCallCatalog calls;
    private final HallCgiProtocol cgi;
    private final Happy4jProperties.Game config;
    private final DdzWalletGrpcCodec walletCodec = new DdzWalletGrpcCodec();
    private final RemoteMissionCatalogService remoteMissions;
    private final MissionDefinitionService missionDefinitions;
    private final ObjectMapper json;

    public HallApplicationService(ClientRuntimeRegistry runtime, ProtocolCallCatalog calls,
                                  HallCgiProtocol cgi, Happy4jProperties properties,
                                  RemoteMissionCatalogService remoteMissions,
                                  MissionDefinitionService missionDefinitions,
                                  ObjectMapper json) {
        this.runtime = runtime;
        this.calls = calls;
        this.cgi = cgi;
        this.config = properties.game();
        this.remoteMissions = remoteMissions;
        this.missionDefinitions = missionDefinitions;
        this.json = json;
    }

    public void loginMissionSystem(GameTransportPort connection, GameCredential credential) {
        try {
            var response = connection.request(calls.missionLogin(credential).request(), config.requestTimeout()).join();
            LOG.info("MissionLogin uin={} response={}", credential.uin(), response.json());
        } catch (Exception failure) {
            LOG.warn("MissionLogin failed uin={} cause={}", credential.uin(), rootMessage(failure));
        }
    }

    public CompletableFuture<WalletBalance> refreshWallet(UUID id) {
        ClientSession session = requireHall(id);
        return runtime.connection(id).grpc(walletCodec.query(session.gameCredential(), config), config.requestTimeout())
                .thenApply(walletCodec::decode).thenApply(balance -> {
                    LOG.info("Wallet refreshed uin={} beans={} diamonds={} goldCoins={}",
                            session.gameCredential().uin(), balance.beans(), balance.diamonds(), balance.goldCoins());
                    session.wallet(balance);
                    return balance;
                });
    }

    public CompletableFuture<PlayerProfile> refreshProfile(UUID id) {
        ClientSession session = requireHall(id);
        return runtime.connection(id).request(cgi.profile(session.gameCredential()), config.requestTimeout())
                .thenApply(cgi::response).thenApply(body -> cgi.profile(body, session.gameCredential()))
                .thenApply(profile -> {
                    session.profile(profile);
                    return profile;
                });
    }

    public CompletableFuture<List<InventoryItem>> refreshInventory(UUID id) {
        ClientSession session = requireHall(id);
        NamedCall call = calls.itemInventory(session.gameCredential(), List.of());
        return runtime.connection(id).request(call.request(), config.requestTimeout()).thenApply(response -> {
            try {
                var root = json.readTree(response.json());
                int result = root.path("resultId").asInt();
                if (result != 0) throw new IllegalStateException("道具背包同步失败: " + result);
                List<InventoryItem> items = new ArrayList<>();
                var values = root.path("items");
                if (values.isArray()) values.forEach(value -> {
                    int itemId = value.path("itemId").asInt();
                    if (itemId > 0) items.add(new InventoryItem(itemId, itemName(itemId),
                            Integer.toUnsignedLong(value.path("count").asInt()), Instant.now()));
                });
                if (items.stream().noneMatch(item -> item.itemId() == SUPER_DOUBLE_ITEM_ID))
                    items.add(new InventoryItem(SUPER_DOUBLE_ITEM_ID, itemName(SUPER_DOUBLE_ITEM_ID), 0,
                            Instant.now()));
                session.inventory(items);
                LOG.info("Inventory refreshed uin={} superDoubleCards={} totalItems={}",
                        session.gameCredential().uin(), session.itemCount(SUPER_DOUBLE_ITEM_ID), items.size());
                return List.copyOf(items);
            } catch (RuntimeException failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("道具背包同步失败", failure);
            }
        });
    }

    public CompletableFuture<List<MissionTask>> refreshMissions(UUID id) {
        ClientSession session = requireHall(id);
        return remoteMissions.synchronize(id).exceptionally(failure -> 0).thenCompose(discovered ->
                        runtime.connection(id).request(cgi.missions(session.gameCredential()), config.requestTimeout()))
                .thenApply(cgi::response).thenApply(body -> {
                    var values = body.path("MissionInfo");
                    if (values.isArray())
                        values.forEach(value -> missionDefinitions.synchronize(value, "CGI_32791"));
                    return cgi.missions(body);
                })
                .thenApply(tasks -> {
                    session.missions(tasks);
                    return tasks;
                });
    }

    public CompletableFuture<ActivityView> claimMission(UUID id, int missionId) {
        ClientSession session = requireHall(id);
        MissionTask mission = session.snapshot().missions().stream()
                .filter(value -> value.missionId() == missionId).findFirst().orElse(null);
        if (missionId == HallCgiProtocol.BANKRUPT_SUPPORT_MISSION) {
            if (session.wallet().beans() >= 2000)
                throw new IllegalStateException("欢乐豆余额高于补助线，当前无需领取补助");
            if (mission != null && mission.periodFinishCount() >= 4)
                throw new IllegalStateException("今日四次欢乐豆补助已经领完");
        } else if (mission == null || !mission.claimable()) {
            throw new IllegalStateException("任务尚未完成或当前不可领取");
        }
        return runtime.connection(id).request(cgi.claimMission(session.gameCredential(), missionId), config.requestTimeout())
                .thenApply(cgi::response).thenCompose(body -> {
                    ActivityView claimed = new ActivityView("mission-" + missionId,
                            missionId == HallCgiProtocol.BANKRUPT_SUPPORT_MISSION
                                    ? "领取每日欢乐豆补助" : "领取任务 " + missionId,
                            15502, "CLAIMED", body.toString(), Instant.now());
                    session.activity(claimed);
                    return CompletableFuture.allOf(ignore(refreshWallet(id), session, "WALLET"),
                                    ignore(refreshMissions(id), session, "MISSIONS"))
                            .thenApply(ignored -> claimed);
                }).exceptionallyCompose(failure -> CompletableFuture.failedFuture(
                        new IllegalStateException(cgi.claimFailureMessage(missionId, failure), failure)));
    }

    public CompletableFuture<List<ActivityView>> refreshActivities(UUID id) {
        ClientSession session = requireHall(id);
        List<CompletableFuture<ActivityView>> operations = new ArrayList<>();
        for (NamedCall call : calls.activityQueries(session.gameCredential())) {
            operations.add(runtime.connection(id).request(call.request(), config.requestTimeout())
                    .handle((response, failure) -> {
                        ActivityView view = new ActivityView(call.id(), call.title(), call.request().messageId(),
                                failure == null ? "READY" : "ERROR",
                                failure == null ? response.json() : rootMessage(failure), Instant.now());
                        session.activity(view);
                        return view;
                    }));
        }
        return CompletableFuture.allOf(operations.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> operations.stream().map(CompletableFuture::join).toList());
    }

    public CompletableFuture<ActivityView> claimSignIn(UUID id, int command, int prizeIndex) {
        ClientSession session = requireHall(id);
        return execute(session, calls.claimSignIn(session.gameCredential(), command, prizeIndex));
    }

    public CompletableFuture<ActivityView> openTreasure(UUID id, int itemId, int itemSeq) {
        ClientSession session = requireHall(id);
        return execute(session, calls.openTreasure(session.gameCredential(), itemId, itemSeq));
    }

    private CompletableFuture<ActivityView> execute(ClientSession session, NamedCall call) {
        return runtime.connection(session.id()).request(call.request(), config.requestTimeout()).thenCompose(response -> {
            ActivityView value = new ActivityView(call.id(), call.title(), call.request().messageId(),
                    "CLAIMED", response.json(), Instant.now());
            session.activity(value);
            return refreshWallet(session.id()).handle((balance, failure) -> value);
        });
    }

    private ClientSession requireHall(UUID id) {
        ClientSession session = runtime.get(id);
        if (session.gameCredential() == null || !runtime.connected(id))
            throw new IllegalStateException("大厅尚未连接");
        return session;
    }

    static String itemName(int itemId) {
        return itemId == SUPER_DOUBLE_ITEM_ID ? "超级加倍卡" : "道具 " + itemId;
    }

    private static CompletableFuture<?> ignore(CompletableFuture<?> operation, ClientSession session, String kind) {
        return operation.exceptionally(failure -> {
            session.publish(kind, "刷新失败: " + rootMessage(failure), null);
            return null;
        });
    }

    private static String rootMessage(Throwable source) {
        Throwable value = source;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }
}
