package cc.superbaby.happy4j.infrastructure.auth.game;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class SafeLoginHttpGatewayTest {
    @Test
    void selectsPositiveMyuinWhenLegacyUinIsPlaceholderZero() throws Exception {
        var root = new ObjectMapper().readTree("{\"result\":0,\"uin\":0,\"myuin\":397911882}");
        assertThat(SafeLoginHttpGateway.resolveGameUin(root)).isEqualTo(397911882L);
    }

    @Test
    void stillAcceptsModernGameuinField() throws Exception {
        var root = new ObjectMapper().readTree("{\"gameuin\":123456,\"myuin\":999}");
        assertThat(SafeLoginHttpGateway.resolveGameUin(root)).isEqualTo(123456L);
    }
}
