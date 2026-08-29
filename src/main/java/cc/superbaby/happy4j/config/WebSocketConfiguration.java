package cc.superbaby.happy4j.config;

import cc.superbaby.happy4j.websocket.AuthenticatedHandshakeInterceptor;
import cc.superbaby.happy4j.websocket.GameWebSocketHandler;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final GameWebSocketHandler handler;
    private final AuthenticatedHandshakeInterceptor authentication;

    public WebSocketConfiguration(GameWebSocketHandler handler, AuthenticatedHandshakeInterceptor authentication) {
        this.handler = handler;
        this.authentication = authentication;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws").addInterceptors(authentication).setAllowedOriginPatterns("*");
    }
}
