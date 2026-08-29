package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.application.port.GameModeCatalogPort;
import cc.superbaby.happy4j.domain.GameMode;
import cc.superbaby.happy4j.infrastructure.persistence.entity.AiConfigurationEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.AiConfigurationRepository;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiConfigurationServiceTest {
    private final AiConfigurationRepository repository = mock(AiConfigurationRepository.class);
    private final GameModeCatalogPort modes = () -> List.of(
            new GameMode(1260, "CLASSIC", "新手场", 0, 10_000, 0, 5, true),
            new GameMode(4260, "LAIZI", "癞子场", 0, 10_000, 0, 5, true));
    private final SecretCipherService secrets = mock(SecretCipherService.class);
    private final AiConfigurationService service = new AiConfigurationService(repository, modes, secrets);

    @Test
    void persistsValidatedPerUserAutomationSettings() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any(AiConfigurationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(secrets.encrypt(any(UUID.class), anyString(), anyString()))
                .thenReturn(new SecretCipherService.EncryptedSecret(new byte[]{1, 2, 3}, new byte[12]));

        var result = service.save(userId,
                new AiConfigurationService.Update(1260, true, true, 100_000, 1_000,
                        "OPENAI", "https://api.example/v1", "expert-model", "secret", false, true));

        assertThat(result.sceneId()).isEqualTo(1260);
        assertThat(result.autoChangeTable()).isTrue();
        assertThat(result.autoClaimBeans()).isTrue();
        assertThat(result.stopAtBeans()).isEqualTo(100_000);
        assertThat(result.stopBelowBeans()).isEqualTo(1_000);
        assertThat(result.inferenceEngine()).isEqualTo("OPENAI");
        assertThat(result.apiBaseUrl()).isEqualTo("https://api.example/v1");
        assertThat(result.apiModel()).isEqualTo("expert-model");
        assertThat(result.apiKeyConfigured()).isTrue();
        assertThat(result.enabled()).isTrue();
    }

    @Test
    void canonicalizesBigModelModelIds() {
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any(AiConfigurationEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.save(userId,
                new AiConfigurationService.Update(1260, true, true, 0, 0,
                        "OPENAI", "https://open.bigmodel.cn/api/paas/v4",
                        "GLM-4.7-Flash", "", false, false));

        assertThat(result.apiModel()).isEqualTo("glm-4.7-flash");
    }

    @Test
    void rejectsUnsupportedModeAndInvertedBeanLimits() {
        UUID userId = UUID.randomUUID();
        assertThatThrownBy(() -> service.save(userId,
                new AiConfigurationService.Update(4260, true, true, 0, 0,
                        "DOUZERO", "", "", "", false, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("经典斗地主");
        assertThatThrownBy(() -> service.save(userId,
                new AiConfigurationService.Update(1260, true, true, 1_000, 2_000,
                        "DOUZERO", "", "", "", false, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("最低欢乐豆");
        assertThatThrownBy(() -> service.save(userId,
                new AiConfigurationService.Update(1260, true, true, 0, 0,
                        "OPENAI", "file:///tmp/model", "model", "", false, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP");
    }
}
