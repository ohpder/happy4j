package cc.superbaby.happy4j.infrastructure.auth.game;

import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.PlatformCredential;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySafeLoginRequestFactoryTest {
    @Test
    void buildsDomesticWechatRequestWithNativeRefererCookieAndRawRsa() throws Exception {
        Happy4jProperties.Auth.Qq qq = new Happy4jProperties.Auth.Qq(716027609, 363, 381,
                "com.qqgame.hlddz", "5c101bee9d0f4cc50ae05f3736a44c84",
                "15", 35, "Google sdk_gphone64_arm64", 2);
        Happy4jProperties.Auth.Wechat wechat = new Happy4jProperties.Auth.Wechat(
                "wx76fc280041c16519", "scope", "", "", "https://itop.qq.com",
                28222, 1, 1, "5.38.102", "sdk-key");
        Happy4jProperties.Game game = new Happy4jProperties.Game(260, 64, 1,
                137887760, 10040714, "35", "legacy",
                "https://qqtranslogin.huanleapp.huanle.qq.com",
                "https://wechatlogin.huanleapp.huanle.qq.com",
                "lotus.invalid", 443, Duration.ofSeconds(5), Duration.ofSeconds(8));
        ObjectMapper json = new ObjectMapper();
        var factory = new LegacySafeLoginRequestFactory(new Happy4jProperties(
                new Happy4jProperties.Auth(qq, wechat), game, null), json);

        var request = factory.create(new PlatformCredential(PlatformCredential.Provider.WECHAT,
                "openid", "access-token", "pf-value", "pf-key", ""));
        var body = json.readTree(request.body());

        assertThat(request.endpoint().getPath()).endsWith("/Common_WeChat_SafeLogin");
        assertThat(request.referer()).isEqualTo("http://emptyreferer.qqgame.qq.com/client/260/64/0");
        assertThat(request.cookie()).contains("channelid=10040714", "gameversion=137887760",
                "appid=wx76fc280041c16519", "openid=openid", "openkey=access-token");
        assertThat(request.randomKey()).hasSize(16);
        assertThat(request.deviceId()).hasSize(25).isEqualTo(body.path("deviceID").asString());
        assertThat(body.path("a0").asString()).hasSize(344);
        assertThat(body.path("b0").asString()).hasSize(344);
        assertThat(body.path("terminaltype").asInt()).isEqualTo(64);
        assertThat(body.has("isfromgamematrix")).isFalse();
    }

    @Test
    void qqEncryptsTheEmptyNativeCustomInfoBlock() throws Exception {
        Happy4jProperties.Auth.Qq qq = new Happy4jProperties.Auth.Qq(716027609, 363, 381,
                "com.qqgame.hlddz", "5c101bee9d0f4cc50ae05f3736a44c84",
                "15", 35, "Google sdk_gphone64_arm64", 2);
        Happy4jProperties.Auth.Wechat wechat = new Happy4jProperties.Auth.Wechat(
                "wx76fc280041c16519", "scope", "", "", "https://itop.qq.com",
                28222, 1, 1, "5.38.102", "sdk-key");
        Happy4jProperties.Game game = new Happy4jProperties.Game(260, 64, 1,
                137887760, 10040714, "35", "legacy", "https://qqlogin.huanleapp.huanle.qq.com",
                "https://wechatlogin.huanleapp.huanle.qq.com", "lotus", 443,
                Duration.ofSeconds(5), Duration.ofSeconds(8));
        ObjectMapper json = new ObjectMapper();
        var factory = new LegacySafeLoginRequestFactory(new Happy4jProperties(
                new Happy4jProperties.Auth(qq, wechat), game, null), json);
        var request = factory.create(new PlatformCredential(PlatformCredential.Provider.QQ,
                "openid", "access-token", "pf", "pf-key", "pay"));
        assertThat(json.readTree(request.body()).path("b0").asString()).hasSize(344);
    }
}
