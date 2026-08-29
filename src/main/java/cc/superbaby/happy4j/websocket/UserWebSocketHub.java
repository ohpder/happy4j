package cc.superbaby.happy4j.websocket;

import cc.superbaby.happy4j.domain.AppPrincipal;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import tools.jackson.databind.ObjectMapper;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserWebSocketHub {
    private final ConcurrentHashMap<UUID, Set<WebSocketSession>> connections = new ConcurrentHashMap<>();
    private final ObjectMapper json;

    public UserWebSocketHub(ObjectMapper json) {
        this.json = json;
    }

    public void add(AppPrincipal user, WebSocketSession session) {
        connections.computeIfAbsent(user.userId(), ignored -> ConcurrentHashMap.newKeySet()).add(session);
    }

    public void remove(AppPrincipal user, WebSocketSession session) {
        Set<WebSocketSession> values = connections.get(user.userId());
        if (values != null) {
            values.remove(session);
            if (values.isEmpty()) connections.remove(user.userId(), values);
        }
    }

    public void send(UUID userId, WebSocketEnvelope envelope) {
        Set<WebSocketSession> values = connections.getOrDefault(userId, Set.of());
        for (WebSocketSession session : values) send(session, envelope);
    }

    public void send(WebSocketSession session, WebSocketEnvelope envelope) {
        if (!session.isOpen()) return;
        try {
            TextMessage message = new TextMessage(json.writeValueAsString(envelope));
            synchronized (session) {
                if (session.isOpen()) session.sendMessage(message);
            }
        } catch (Exception ignored) {
            try {
                session.close();
            } catch (Exception ignoredAgain) {
            }
        }
    }
}
