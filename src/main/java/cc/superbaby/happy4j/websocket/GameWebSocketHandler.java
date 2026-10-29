package cc.superbaby.happy4j.websocket;

import cc.superbaby.happy4j.application.HappyDdzClientService;
import cc.superbaby.happy4j.application.AiConfigurationService;
import cc.superbaby.happy4j.application.OpenAiDecisionService;
import cc.superbaby.happy4j.application.GameRecordService;
import cc.superbaby.happy4j.domain.AppPrincipal;
import cc.superbaby.happy4j.domain.ClientSession;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {
    private final HappyDdzClientService clients;
    private final UserWebSocketHub hub;
    private final GameRealtimeGateway realtime;
    private final QrLoginCoordinator qr;
    private final AiConfigurationService aiConfigurations;
    private final OpenAiDecisionService aiDecisions;
    private final GameRecordService gameRecords;
    private final ObjectMapper json;

    public GameWebSocketHandler(HappyDdzClientService clients, UserWebSocketHub hub,
                                GameRealtimeGateway realtime, QrLoginCoordinator qr,
                                AiConfigurationService aiConfigurations,
                                OpenAiDecisionService aiDecisions, GameRecordService gameRecords,
                                ObjectMapper json) {
        this.clients = clients;
        this.hub = hub;
        this.realtime = realtime;
        this.qr = qr;
        this.aiConfigurations = aiConfigurations;
        this.aiDecisions = aiDecisions;
        this.gameRecords = gameRecords;
        this.json = json;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        AppPrincipal user = principal(session);
        hub.add(user, session);
        realtime.watchUser(user.userId());
        sendResult(session, "BOOT", GameCommand.BOOT, boot(user));
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        String requestId = null;
        try {
            JsonNode root = json.readTree(message.getPayload());
            requestId = root.path("requestId").asString(UUID.randomUUID().toString());
            GameCommand command = GameCommand.valueOf(root.path("command").asString()
                    .trim().toUpperCase(Locale.ROOT));
            JsonNode payload = root.path("payload");
            CompletionStage<?> operation = execute(principal(socket), command, payload);
            String finalRequestId = requestId;
            operation.whenComplete((value, failure) -> {
                if (failure == null) sendResult(socket, finalRequestId, command, value);
                else hub.send(socket, WebSocketEnvelope.of("ERROR", finalRequestId,
                        Map.of("command", command, "message", rootMessage(failure))));
            });
        } catch (Exception failure) {
            hub.send(socket, WebSocketEnvelope.of("ERROR", requestId,
                    Map.of("message", rootMessage(failure))));
        }
    }

    private CompletionStage<?> execute(AppPrincipal user, GameCommand command, JsonNode payload) {
        if (command == GameCommand.BOOT) return done(boot(user));
        if (command == GameCommand.SESSION_CREATE) {
            ClientSession.Snapshot snapshot = clients.createSession(user.userId());
            realtime.watch(clients.ownedSession(user.userId(), snapshot.id()));
            return done(snapshot);
        }
        if (command == GameCommand.SESSION_RESUME) {
            return clients.resumeSavedLogin(user.userId()).thenApply(snapshot -> {
                realtime.watch(clients.ownedSession(user.userId(), snapshot.id()));
                return snapshot;
            });
        }
        if (command == GameCommand.AI_CONFIG_GET) return done(aiConfigurations.get(user.userId()));
        if (command == GameCommand.AI_CONFIG_SAVE) {
            var update = new AiConfigurationService.Update(
                    payload.path("sceneId").asInt(),
                    payload.path("hostingMode").asString("BROWSER"),
                    payload.path("autoChangeTable").asBoolean(true),
                    payload.path("autoClaimBeans").asBoolean(true),
                    payload.path("stopAtBeans").asLong(),
                    payload.path("stopBelowBeans").asLong(),
                    payload.path("inferenceEngine").asString("DOUZERO"),
                    payload.path("apiBaseUrl").asString(),
                    payload.path("apiModel").asString(),
                    payload.path("apiKey").asString(),
                    payload.path("clearApiKey").asBoolean(),
                    payload.path("enabled").asBoolean());
            return done(aiConfigurations.save(user.userId(), update));
        }
        if (command == GameCommand.AI_STOP) {
            String value = payload.path("mode").asString("IMMEDIATE").trim().toUpperCase(Locale.ROOT);
            AiConfigurationService.StopMode mode;
            try {
                mode = AiConfigurationService.StopMode.valueOf(value);
            } catch (IllegalArgumentException failure) {
                throw new IllegalArgumentException("未知托管停止方式");
            }
            return done(aiConfigurations.requestStop(user.userId(), mode));
        }
        if (command == GameCommand.AI_PROVIDER_TEST) return aiDecisions.test(user.userId());
        if (command == GameCommand.RECORDS_GET)
            return done(gameRecords.page(user.userId(), payload.path("limit").asInt(30)));
        UUID sessionId = UUID.fromString(payload.path("sessionId").asString());
        ClientSession session = clients.ownedSession(user.userId(), sessionId);
        return switch (command) {
            case SESSION_CLOSE -> {
                qr.stop(sessionId);
                clients.closeSession(user.userId(), sessionId);
                yield done(Map.of("closed", true));
            }
            case SNAPSHOT_GET -> done(session.snapshot());
            case QR_START -> startQr(session, payload.path("provider").asString("qq"));
            case WALLET_REFRESH -> clients.refreshWallet(sessionId);
            case PROFILE_REFRESH -> clients.refreshProfile(sessionId);
            case MISSIONS_REFRESH -> clients.refreshMissions(sessionId);
            case MISSION_CLAIM -> clients.claimMission(sessionId, payload.path("missionId").asInt());
            case ACTIVITIES_REFRESH -> clients.refreshActivities(sessionId);
            case SIGN_IN_CLAIM -> clients.claimSignIn(sessionId, payload.path("commandCode").asInt(13),
                    payload.path("prizeIndex").asInt());
            case TREASURE_OPEN -> clients.openTreasure(sessionId, payload.path("itemId").asInt(),
                    payload.path("itemSeq").asInt());
            case AI_DECIDE -> aiDecisions.decide(user.userId(), session, payload);
            case MODE_ENTER -> clients.enterMode(sessionId, payload.path("sceneId").asInt());
            case READY -> clients.ready(sessionId);
            case ORDER -> clients.order(sessionId, payload.path("accept").asBoolean());
            case MULTIPLE -> clients.multiple(sessionId, payload.path("accept").asBoolean());
            case SUPER_MULTIPLE -> clients.superMultiple(sessionId);
            case TRUST -> clients.trust(sessionId, payload.path("enabled").asBoolean());
            case PLAY -> clients.play(sessionId, cards(payload.path("cards")), payload.path("pass").asBoolean(), null);
            case CHANGE_TABLE -> clients.changeTable(sessionId);
            case LEAVE_ROOM -> clients.leaveRoom(sessionId);
            default -> throw new IllegalArgumentException("不支持的命令: " + command);
        };
    }

    private CompletionStage<?> startQr(ClientSession session, String provider) {
        var challenge = clients.startQr(session.id(), provider);
        byte[] image = clients.qrImage(session.id());
        String mime = image.length > 2 && image[0] == (byte) 0xff && image[1] == (byte) 0xd8
                ? "image/jpeg" : "image/png";
        qr.start(session);
        return done(Map.of("sessionId", session.id(), "provider", provider,
                "expiresIn", challenge.expiresInSeconds(), "image",
                "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image)));
    }

    private Map<String, Object> boot(AppPrincipal user) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("user", user);
        value.put("modes", clients.gameModes());
        value.put("sessions", clients.userSessions(user.userId()));
        value.put("aiConfig", aiConfigurations.get(user.userId()));
        return value;
    }

    private void sendResult(WebSocketSession socket, String requestId, GameCommand command, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("command", command);
        payload.put("data", value);
        hub.send(socket, WebSocketEnvelope.of("RESULT", requestId, payload));
    }

    private static List<Integer> cards(JsonNode value) {
        List<Integer> cards = new ArrayList<>();
        if (value.isArray()) value.forEach(card -> cards.add(card.asInt()));
        return List.copyOf(cards);
    }

    private static CompletableFuture<Object> done(Object value) {
        return CompletableFuture.completedFuture(value);
    }

    private static AppPrincipal principal(WebSocketSession session) {
        Object value = session.getAttributes().get(AuthenticatedHandshakeInterceptor.PRINCIPAL);
        if (value instanceof AppPrincipal principal) return principal;
        throw new IllegalStateException("WebSocket 未认证");
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Object value = session.getAttributes().get(AuthenticatedHandshakeInterceptor.PRINCIPAL);
        if (value instanceof AppPrincipal principal) hub.remove(principal, session);
    }
}
