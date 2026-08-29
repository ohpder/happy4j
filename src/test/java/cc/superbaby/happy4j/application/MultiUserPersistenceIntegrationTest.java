package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.infrastructure.persistence.repository.AppUserRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameLoginStateRepository;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.PlatformCredential;

import java.util.Map;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class MultiUserPersistenceIntegrationTest {
    @Autowired
    AuthService auth;
    @Autowired
    HappyDdzClientService clients;
    @Autowired
    AppUserRepository users;
    @Autowired
    CredentialVaultService vault;
    @Autowired
    ServerConfigService config;
    @Autowired
    MissionDefinitionService missions;
    @Autowired
    ObjectMapper json;
    @Autowired
    GameLoginStateRepository loginStates;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void registrationHashesPasswordsAndGameSessionsAreOwnerIsolated() throws Exception {
        var first = auth.register("alice_01", "correct-horse-1", "Alice");
        var second = auth.register("bob_01", "correct-horse-2", "Bob");

        assertThat(users.findByUsernameIgnoreCase("ALICE_01").orElseThrow().getPasswordHash())
                .doesNotContain("correct-horse-1").startsWith("$2");
        assertThat(auth.authenticate(first.token()).userId()).isEqualTo(first.user().userId());

        var session = clients.createSession(first.user().userId());
        assertThat(clients.ownedSession(first.user().userId(), session.id()).ownerId())
                .isEqualTo(first.user().userId());
        assertThatThrownBy(() -> clients.ownedSession(second.user().userId(), session.id()))
                .isInstanceOf(IllegalArgumentException.class);

        GameCredential credential = new GameCredential(397911882, 42, PlatformCredential.Provider.WECHAT,
                new byte[16], new byte[]{1, 2, 3}, "0123456789abcdef", "openid", "openkey", "pay", "pf", "pfkey", 11, 0, Map.of("deviceID", "dev"));
        vault.store(first.user().userId(), credential);
        assertThat(new String(loginStates.findByUserIdAndStatus(first.user().userId(), "ACTIVE").get(0)
                .getEncryptedPayload(), java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("openkey");
        GameCredential restored = vault.restore(first.user().userId()).orElseThrow();
        assertThat(restored.uin()).isEqualTo(397911882);
        assertThat(restored.sessionTicket()).containsExactly(1, 2, 3);
        assertThat(restored.openKey()).isEqualTo("openkey");

        users.save(new cc.superbaby.happy4j.infrastructure.persistence.entity.AppUserEntity(
                java.util.UUID.randomUUID(), "admin", passwordEncoder.encode("administrator-pass"),
                "Administrator", "USER"));
        var admin = auth.login("admin", "administrator-pass");
        assertThat(admin.user().admin()).isTrue();
        assertThat(users.findByUsernameIgnoreCase("admin").orElseThrow().getRole()).isEqualTo("ADMIN");
        config.put("feature.test", Map.of("enabled", true), "test");
        assertThat(config.get("feature.test").path("enabled").asBoolean()).isTrue();
        missions.synchronize(json.readTree("{\"MissionID\":9001,\"MissionDesc\":\"在线任务\"}"), "REMOTE_TEST");
        assertThat(missions.ids()).contains(9001);
        assertThat(missions.title(9001)).isEqualTo("在线任务");
        assertThat(missions.title(2666)).isEqualTo("完成不洗牌玩法对局");
    }
}
