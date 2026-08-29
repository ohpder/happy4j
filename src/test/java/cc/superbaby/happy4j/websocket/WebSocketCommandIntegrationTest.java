package cc.superbaby.happy4j.websocket;

import cc.superbaby.happy4j.application.AuthService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketCommandIntegrationTest {
    @LocalServerPort
    int port;
    @Autowired
    AuthService auth;

    @Test
    void authenticatedSocketBootsAndCreatesAnOwnedSessionWithoutPolling() throws Exception {
        String username = "ws_" + UUID.randomUUID().toString().substring(0, 8);
        String token = auth.register(username, "websocket-pass", "Socket User").token();
        LinkedBlockingQueue<String> messages = new LinkedBlockingQueue<>();
        var handler = new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                messages.add(message.getPayload());
            }
        };
        StandardWebSocketClient client = new StandardWebSocketClient();
        WebSocketSession session = client.execute(handler,
                "ws://127.0.0.1:" + port + "/ws?token=" + token).get(5, TimeUnit.SECONDS);

        assertThat(messages.poll(5, TimeUnit.SECONDS)).contains("\"type\":\"RESULT\"", "\"command\":\"BOOT\"");
        session.sendMessage(new TextMessage("""
                {"requestId":"create-1","command":"SESSION_CREATE","payload":{}}
                """));
        assertThat(messages.poll(5, TimeUnit.SECONDS)).contains("\"requestId\":\"create-1\"", "\"phase\":\"CREATED\"");
        session.close();
    }
}
