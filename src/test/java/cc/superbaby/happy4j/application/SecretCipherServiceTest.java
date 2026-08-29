package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.config.Happy4jProperties;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherServiceTest {
    private final SecretCipherService cipher = new SecretCipherService(
            new Happy4jProperties(null, null, null));

    @Test
    void encryptsProviderKeyWithOwnerAndPurposeBoundAuthentication() {
        UUID owner = UUID.randomUUID();
        var encrypted = cipher.encrypt(owner, "openai-api-key", "sk-secret-value");

        assertThat(encrypted.encrypted()).isNotEqualTo("sk-secret-value".getBytes());
        assertThat(cipher.decrypt(owner, "openai-api-key",
                encrypted.encrypted(), encrypted.iv())).isEqualTo("sk-secret-value");
        assertThatThrownBy(() -> cipher.decrypt(UUID.randomUUID(), "openai-api-key",
                encrypted.encrypted(), encrypted.iv())).isInstanceOf(IllegalStateException.class);
    }
}
