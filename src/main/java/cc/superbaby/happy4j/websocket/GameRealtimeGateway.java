package cc.superbaby.happy4j.websocket;

import cc.superbaby.happy4j.application.HappyDdzClientService;
import cc.superbaby.happy4j.domain.ClientSession;

import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class GameRealtimeGateway {
    private final UserWebSocketHub hub;
    private final HappyDdzClientService clients;
    private final Set<UUID> watched = ConcurrentHashMap.newKeySet();

    public GameRealtimeGateway(UserWebSocketHub hub, HappyDdzClientService clients) {
        this.hub = hub;
        this.clients = clients;
    }

    public void watch(ClientSession session) {
        if (!watched.add(session.id())) return;
        session.subscribe(event -> {
            hub.send(session.ownerId(), WebSocketEnvelope.of("EVENT", null, event));
            hub.send(session.ownerId(), WebSocketEnvelope.of("SNAPSHOT", null, session.snapshot()));
        });
    }

    public void watchUser(UUID userId) {
        for (var snapshot : clients.userSessions(userId))
            watch(clients.ownedSession(userId, snapshot.id()));
    }
}
