package cc.superbaby.happy4j.infrastructure.auth.game;

import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.PlatformCredential;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class GCloudSafeLoginRequestFactoryTest {
    private final GCloudSafeLoginRequestFactory factory = new GCloudSafeLoginRequestFactory(new Happy4jProperties.Game(
            260, 64, 1, 137887760, 10040714, "35", "gcloud",
            "https://qqtranslogin.huanleapp.huanle.qq.com",
            "https://wechatlogin.huanleapp.huanle.qq.com",
            "lotus.invalid", 443, Duration.ofSeconds(5), Duration.ofSeconds(8)));

    @Test
    void buildsExactWechatMsdkV5GCloudRequest() {
        var credential = new PlatformCredential(PlatformCredential.Provider.WECHAT,
                "wx open/id", "token+value", "pf", "pf-key", "");

        var request = factory.create(credential);

        assertThat(request.endpoint().toString()).isEqualTo(
                "https://wechatlogin.huanleapp.huanle.qq.com/cgi-bin/CommonMobileCGI/Common_SafeLogin_GCloud");
        assertThat(request.body()).isEqualTo(
                "clienttype=64&accounttype=1&clientversion=137887760&gameid=260&openid=wx+open%2Fid&token=token%2Bvalue");
    }

    @Test
    void mapsQqToAccountTypeTwoAndProviderEndpoint() {
        var credential = new PlatformCredential(PlatformCredential.Provider.QQ,
                "qq-open", "qq-token", "pf", "pf-key", "pay");

        var request = factory.create(credential);

        assertThat(request.endpoint().getHost()).isEqualTo("qqtranslogin.huanleapp.huanle.qq.com");
        assertThat(request.body()).contains("accounttype=2", "openid=qq-open", "token=qq-token");
    }
}
