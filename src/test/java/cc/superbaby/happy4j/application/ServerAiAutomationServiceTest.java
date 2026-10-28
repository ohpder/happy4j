package cc.superbaby.happy4j.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ServerAiAutomationServiceTest {
    @Test
    void usesTheStricterOfConfiguredAndSceneEntryLimits() {
        assertThat(ServerAiAutomationService.effectiveStopBelowBeans(0, 1_000)).isEqualTo(1_000);
        assertThat(ServerAiAutomationService.effectiveStopBelowBeans(2_000, 1_000)).isEqualTo(2_000);
        assertThat(ServerAiAutomationService.effectiveStopBelowBeans(1_000, 2_000)).isEqualTo(2_000);
    }

    @Test
    void doesNotCreateALimitWhenNeitherSourceHasOne() {
        assertThat(ServerAiAutomationService.effectiveStopBelowBeans(0, 0)).isZero();
    }
}
