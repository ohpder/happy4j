package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.ClientSession;
import cc.superbaby.happy4j.domain.GameStage;
import cc.superbaby.happy4j.domain.MissionTask;
import cc.superbaby.happy4j.domain.TableAction;
import cc.superbaby.happy4j.domain.TableSnapshot;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Keeps server-hosted AI sessions moving without depending on a browser polling loop.
 */
@Service
public final class ServerAiAutomationService {
    private static final Logger LOG = LoggerFactory.getLogger(ServerAiAutomationService.class);
    private static final int SUPPORT_MISSION_ID = 1563;
    private static final int SUPER_DOUBLE_ITEM_ID = 1030108;

    private final AiConfigurationService configurations;
    private final HappyDdzClientService clients;
    private final OpenAiDecisionService openAi;
    private final ServerAiDecisionService local;
    private final ObjectMapper json;
    private final ScheduledExecutorService ticker;
    private final ExecutorService workers;
    private final ConcurrentHashMap<UUID, Boolean> running = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Long> lastSupportCheck = new ConcurrentHashMap<>();

    public ServerAiAutomationService(AiConfigurationService configurations, HappyDdzClientService clients,
                                     OpenAiDecisionService openAi, ServerAiDecisionService local,
                                     ObjectMapper json) {
        this.configurations = configurations;
        this.clients = clients;
        this.openAi = openAi;
        this.local = local;
        this.json = json;
        ThreadFactory threads = runnable -> {
            Thread thread = new Thread(runnable, "server-ai-worker");
            thread.setDaemon(true);
            return thread;
        };
        this.ticker = Executors.newSingleThreadScheduledExecutor(threads);
        this.workers = Executors.newFixedThreadPool(4, threads);
    }

    @PostConstruct
    void start() {
        ticker.scheduleWithFixedDelay(this::scan, 500, 900, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    void stop() {
        ticker.shutdownNow();
        workers.shutdownNow();
    }

    private void scan() {
        try {
            for (AiConfigurationService.ServerConfiguration configured : configurations.serverConfigurations()) {
                UUID userId = configured.userId();
                if (running.putIfAbsent(userId, Boolean.TRUE) == null)
                    workers.execute(() -> runUser(configured));
            }
        } catch (RuntimeException failure) {
            LOG.debug("扫描服务器 AI 配置失败: {}", rootMessage(failure));
        }
    }

    private void runUser(AiConfigurationService.ServerConfiguration configured) {
        UUID userId = configured.userId();
        try {
            ClientSession session = findSession(userId, configured.configuration().sceneId());
            if (session == null) {
                try {
                    clients.resumeSavedLogin(userId).join();
                    publish(userId, null, "服务器托管已恢复游戏登录", Map.of());
                } catch (RuntimeException failure) {
                    LOG.debug("服务器 AI 暂无可用游戏会话 user={}: {}", userId, rootMessage(failure));
                }
                return;
            }
            if (session.gameCredential() == null) return;
            if (handleLimits(configured.configuration(), session)) return;
            TableSnapshot table = session.table();
            if (table == null || table.stage() == GameStage.IDLE) {
                clients.enterMode(session.id(), configured.configuration().sceneId()).join();
                publish(userId, session, "服务器托管正在进入选定场次", Map.of("sceneId",
                        configured.configuration().sceneId()));
                return;
            }
            if (table.stage() == GameStage.TIMED_OUT) {
                if (table.availableActions().contains(TableAction.CHANGE_TABLE))
                    clients.changeTable(session.id()).join();
                return;
            }
            if (table.stage() == GameStage.SETTLED) {
                settle(configured.configuration(), session, table);
                return;
            }
            if (table.stage() == GameStage.CALLING_LANDLORD) {
                order(configured.configuration(), session, table);
                return;
            }
            if (table.stage() == GameStage.DOUBLING) {
                multiple(configured.configuration(), session, table);
                return;
            }
            if (table.stage() == GameStage.PLAYING) play(configured.configuration(), session, table);
        } catch (RuntimeException failure) {
            LOG.debug("服务器 AI 执行失败 user={}: {}", userId, rootMessage(failure));
            publish(userId, null, "服务器托管等待下一次同步", Map.of("message", rootMessage(failure)));
        } finally {
            running.remove(userId);
        }
    }

    private ClientSession findSession(UUID userId, int sceneId) {
        return clients.userSessions(userId).stream()
                .map(snapshot -> clients.ownedSession(userId, snapshot.id()))
                .filter(session -> {
                    TableSnapshot table = session.table();
                    return table.sceneId() == sceneId || table.sceneId() == 0;
                })
                .findFirst().orElse(null);
    }

    private boolean handleLimits(cc.superbaby.happy4j.domain.AiConfiguration configuration,
                                 ClientSession session) {
        if (session.wallet().refreshedAt().equals(Instant.EPOCH)) {
            clients.refreshWallet(session.id()).join();
            return true;
        }
        long beans = session.wallet().beans();
        if (configuration.autoClaimBeans() && beans < 2_000
                && shouldCheckSupport(session.ownerId())) {
            List<MissionTask> missions = clients.refreshMissions(session.id()).join();
            MissionTask support = missions.stream()
                    .filter(mission -> mission.missionId() == SUPPORT_MISSION_ID).findFirst().orElse(null);
            if (support != null && support.periodFinishCount() < 4 && support.claimable()) {
                clients.claimMission(session.id(), SUPPORT_MISSION_ID).join();
                clients.refreshWallet(session.id()).join();
                publish(session.ownerId(), session, "服务器托管已领取欢乐豆补助", Map.of("beans", beans));
                return true;
            }
        }
        if (configuration.stopAtBeans() > 0 && beans >= configuration.stopAtBeans()) {
            configurations.disable(session.ownerId());
            publish(session.ownerId(), session, "达到目标欢乐豆，服务器托管已停止",
                    Map.of("beans", beans, "limit", configuration.stopAtBeans()));
            return true;
        }
        if (configuration.stopBelowBeans() > 0 && beans < configuration.stopBelowBeans()) {
            configurations.disable(session.ownerId());
            publish(session.ownerId(), session, "欢乐豆低于保护线，服务器托管已停止",
                    Map.of("beans", beans, "limit", configuration.stopBelowBeans()));
            return true;
        }
        return false;
    }

    private boolean shouldCheckSupport(UUID userId) {
        long now = System.currentTimeMillis();
        Long previous = lastSupportCheck.put(userId, now);
        return previous == null || now - previous >= 30_000;
    }

    private void settle(cc.superbaby.happy4j.domain.AiConfiguration configuration,
                        ClientSession session, TableSnapshot table) {
        if (configuration.autoChangeTable() && table.availableActions().contains(TableAction.CHANGE_TABLE))
            clients.changeTable(session.id()).join();
        else if (table.availableActions().contains(TableAction.READY)) clients.ready(session.id()).join();
    }

    private void order(cc.superbaby.happy4j.domain.AiConfiguration configuration,
                       ClientSession session, TableSnapshot table) {
        if (!table.availableActions().contains(TableAction.CALL_LANDLORD)
                && !table.availableActions().contains(TableAction.PASS_LANDLORD)
                && !table.availableActions().contains(TableAction.ROB_LANDLORD)
                && !table.availableActions().contains(TableAction.PASS_ROB)) return;
        boolean accept = local.chooseLandlord(table);
        if ("OPENAI".equals(configuration.inferenceEngine())) {
            try {
                accept = openAi.decide(session.ownerId(), session, phase("ORDER")).join()
                        .action().equals("ACCEPT");
            } catch (RuntimeException failure) {
                LOG.debug("服务器 AI 叫抢模型失败，使用规则策略: {}", rootMessage(failure));
            }
        }
        clients.order(session.id(), accept).join();
        publish(session.ownerId(), session, accept ? "服务器 AI 叫/抢地主" : "服务器 AI 不叫/不抢",
                Map.of("phase", "ORDER", "accept", accept));
    }

    private void multiple(cc.superbaby.happy4j.domain.AiConfiguration configuration,
                          ClientSession session, TableSnapshot table) {
        ServerAiDecisionService.DoubleDecision decision = local.chooseDouble(table,
                session.itemCount(table.superDoubleItemId() > 0 ? table.superDoubleItemId() : SUPER_DOUBLE_ITEM_ID));
        if ("OPENAI".equals(configuration.inferenceEngine())) {
            try {
                String action = openAi.decide(session.ownerId(), session, phase("DOUBLE")).join().action();
                decision = new ServerAiDecisionService.DoubleDecision(action, decision.strength());
            } catch (RuntimeException failure) {
                LOG.debug("服务器 AI 加倍模型失败，使用规则策略: {}", rootMessage(failure));
            }
        }
        switch (decision.action()) {
            case "SUPER_DOUBLE" -> clients.superMultiple(session.id()).join();
            case "DOUBLE" -> clients.multiple(session.id(), true).join();
            default -> clients.multiple(session.id(), false).join();
        }
        publish(session.ownerId(), session, "服务器 AI 加倍决策", Map.of(
                "phase", "DOUBLE", "action", decision.action(), "strength", decision.strength()));
    }

    private void play(cc.superbaby.happy4j.domain.AiConfiguration configuration,
                      ClientSession session, TableSnapshot table) {
        if (table.trusted() && table.availableActions().contains(TableAction.CANCEL_TRUST)) {
            clients.trust(session.id(), false).join();
            return;
        }
        if (table.turnSeatId() != table.seatId()) return;
        if (!table.availableActions().contains(TableAction.PLAY)
                && !table.availableActions().contains(TableAction.PASS)) return;
        ServerAiDecisionService.PlayDecision localDecision = local.choosePlay(table);
        ServerAiDecisionService.PlayDecision decision = localDecision;
        if ("OPENAI".equals(configuration.inferenceEngine()) && !local.legalMoves(table).isEmpty()) {
            try {
                var remote = openAi.decide(session.ownerId(), session, playPayload(local.legalMoves(table))).join();
                decision = new ServerAiDecisionService.PlayDecision(remote.cards(),
                        "PASS".equals(remote.action()), localDecision.candidates(), remote.reason());
            } catch (RuntimeException failure) {
                LOG.debug("服务器 AI 出牌模型失败，使用规则策略: {}", rootMessage(failure));
            }
        }
        clients.play(session.id(), decision.cards(), decision.pass(), null).join();
        publish(session.ownerId(), session, decision.pass() ? "服务器 AI 选择不出" : "服务器 AI 已出牌",
                Map.of("phase", "PLAY", "cards", decision.cards(), "candidates", decision.candidates(),
                        "reason", decision.reason()));
    }

    private ObjectNode phase(String value) {
        ObjectNode payload = json.createObjectNode();
        payload.put("phase", value);
        return payload;
    }

    private ObjectNode playPayload(List<List<Integer>> moves) {
        ObjectNode payload = phase("PLAY");
        ArrayNode candidates = payload.putArray("candidates");
        moves.forEach(move -> {
            ArrayNode cards = candidates.addArray();
            move.forEach(cards::add);
        });
        return payload;
    }

    private static void publish(UUID userId, ClientSession session, String message, Map<String, ?> detail) {
        if (session != null) session.publish("AI_SERVER", message, detail);
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }
}
