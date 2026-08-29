package cc.superbaby.happy4j.infrastructure.auth.wechat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeChatQrLoginGatewayTest {
    @Test
    void matchesWechatOpenSdkSignatureVector() {
        String ticket = "-p3A5zVP95IuafPhzA6lRR95_F9nZEBfJ_n4E9t8ZFWKJTDPOwccVQhHCwDBmvLkayF_jh-m9HOExhumOziDWA";
        assertThat(WeChatQrLoginGateway.signature("appid", "noncestr", ticket, "1417508194"))
                .isEqualTo("429eaaa13fd71efbc3fd344d0a9a9126835e7303");
    }
}
