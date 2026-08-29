package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.AppPrincipal;
import cc.superbaby.happy4j.infrastructure.persistence.entity.AppUserEntity;
import cc.superbaby.happy4j.infrastructure.persistence.entity.AuthTokenEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.AppUserRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.AuthTokenRepository;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final AppUserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final Happy4jProperties.Security config;
    private final SecureRandom random = new SecureRandom();

    public AuthService(AppUserRepository users, AuthTokenRepository tokens,
                       PasswordEncoder passwords, Happy4jProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwords = passwords;
        this.config = properties.security();
    }

    @Transactional
    public AuthResult register(String username, String password, String displayName) {
        String normalized = normalize(username);
        validatePassword(password);
        if (users.existsByUsernameIgnoreCase(normalized))
            throw new IllegalArgumentException("用户名已存在");
        String name = displayName == null || displayName.isBlank() ? normalized : displayName.trim();
        AppUserEntity user = users.save(new AppUserEntity(UUID.randomUUID(), normalized,
                passwords.encode(password), truncate(name, 64), isAdmin(normalized) ? "ADMIN" : "USER"));
        return issue(user);
    }

    @Transactional
    public AuthResult login(String username, String password) {
        AppUserEntity user = users.findByUsernameIgnoreCase(normalize(username))
                .orElseThrow(() -> new IllegalArgumentException("用户名或密码错误"));
        if (!user.isEnabled() || !passwords.matches(password, user.getPasswordHash()))
            throw new IllegalArgumentException("用户名或密码错误");
        // Existing installations may have created the configured administrator before
        // the role column existed. Reconcile it during a successful password login.
        if (isAdmin(user.getUsername()) && !"ADMIN".equals(user.getRole()))
            user.assignRole("ADMIN");
        return issue(user);
    }

    @Transactional
    public AppPrincipal authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return null;
        AuthTokenEntity token = tokens.findByTokenHashAndRevokedAtIsNull(hash(rawToken)).orElse(null);
        if (token == null || !token.getExpiresAt().isAfter(Instant.now()) || !token.getUser().isEnabled())
            return null;
        token.touch();
        AppUserEntity user = token.getUser();
        return new AppPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;
        tokens.findByTokenHashAndRevokedAtIsNull(hash(rawToken)).ifPresent(AuthTokenEntity::revoke);
    }

    private AuthResult issue(AppUserEntity user) {
        var active = tokens.findByUserIdAndRevokedAtIsNullOrderByCreatedAtAsc(user.getId());
        int max = Math.max(1, config.maxSessionsPerUser());
        for (int i = 0; i <= active.size() - max; i++) active.get(i).revoke();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = Instant.now().plus(config.tokenTtl());
        tokens.save(new AuthTokenEntity(UUID.randomUUID(), user, hash(raw), expiresAt));
        return new AuthResult(raw, expiresAt,
                new AppPrincipal(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole()));
    }

    private static String normalize(String value) {
        if (value == null) throw new IllegalArgumentException("用户名不能为空");
        String result = value.trim().toLowerCase(Locale.ROOT);
        if (!result.matches("[a-z0-9_]{3,32}"))
            throw new IllegalArgumentException("用户名需为 3-32 位字母、数字或下划线");
        return result;
    }

    private static void validatePassword(String value) {
        if (value == null || value.length() < 8 || value.length() > 72)
            throw new IllegalArgumentException("密码长度需为 8-72 位");
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }

    private boolean isAdmin(String username) {
        return config.adminUsers().stream().anyMatch(username::equalsIgnoreCase);
    }

    public record AuthResult(String token, Instant expiresAt, AppPrincipal user) {
    }
}
