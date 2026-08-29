package cc.superbaby.happy4j.config;

import cc.superbaby.happy4j.application.HappyDdzClientService;
import cc.superbaby.happy4j.domain.AppPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

@Component
public class SessionOwnershipInterceptor implements HandlerInterceptor {
    private final HappyDdzClientService clients;

    public SessionOwnershipInterceptor(HappyDdzClientService clients) {
        this.clients = clients;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        @SuppressWarnings("unchecked") Map<String, String> variables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        String id = variables == null ? null : variables.get("id");
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (id != null && principal instanceof AppPrincipal user)
            clients.assertOwner(user.userId(), UUID.fromString(id));
        return true;
    }
}
