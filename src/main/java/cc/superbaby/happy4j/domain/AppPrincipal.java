package cc.superbaby.happy4j.domain;

import java.util.UUID;

public record AppPrincipal(UUID userId, String username, String displayName, String role) {
    public boolean admin() {
        return "ADMIN".equals(role);
    }
}
