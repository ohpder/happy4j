package cc.superbaby.happy4j.websocket;

import cc.superbaby.happy4j.application.AuthService;

import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class AuthenticatedHandshakeInterceptor implements HandshakeInterceptor {
    public static final String PRINCIPAL = "happy4j.principal";
    private final AuthService auth;

    public AuthenticatedHandshakeInterceptor(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = query(request.getURI().getRawQuery(), "token");
        var principal = auth.authenticate(token);
        if (principal == null) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(PRINCIPAL, principal);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
    }

    private static String query(String query, String name) {
        if (query == null) return "";
        for (String part : query.split("&")) {
            int split = part.indexOf('=');
            if (split > 0 && part.substring(0, split).equals(name))
                return URLDecoder.decode(part.substring(split + 1), StandardCharsets.UTF_8);
        }
        return "";
    }
}
