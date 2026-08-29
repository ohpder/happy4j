package cc.superbaby.happy4j.web;

import cc.superbaby.happy4j.application.AuthService;
import cc.superbaby.happy4j.domain.AppPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public AuthService.AuthResult register(@Valid @RequestBody RegisterRequest request) {
        return auth.register(request.username(), request.password(), request.displayName());
    }

    @PostMapping("/login")
    public AuthService.AuthResult login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request.username(), request.password());
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(@RequestHeader("Authorization") String authorization) {
        auth.logout(authorization.replaceFirst("(?i)^Bearer\\s+", ""));
        return Map.of("ok", true);
    }

    @GetMapping("/me")
    public AppPrincipal me(@AuthenticationPrincipal AppPrincipal principal) {
        return principal;
    }

    public record RegisterRequest(@NotBlank @Size(min = 3, max = 32) String username,
                                  @NotBlank @Size(min = 8, max = 72) String password,
                                  @Size(max = 64) String displayName) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }
}
