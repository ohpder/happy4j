package cc.superbaby.happy4j.infrastructure.auth.qq;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import cc.superbaby.happy4j.infrastructure.auth.msdk.MsdkDeviceContext;

import static org.assertj.core.api.Assertions.assertThat;

class MsdkQqAuthClientTest {
    @Test
    void mapsOpenSdkProxyFieldsIntoMsdkScanLoginBody() throws Exception {
        String body = MsdkQqAuthClient.qqLoginBody("OPEN", "ACCESS", "PAY", "KEY", new ObjectMapper(),
                new MsdkDeviceContext());

        assertThat(body).contains("\"channel_dis\":\"QQ\"", "\"openid\":\"OPEN\"",
                "\"access_token\":\"ACCESS\"", "\"pay_token\":\"PAY\"", "\"key\":\"KEY\"",
                "\"QRCode\":true", "\"login_type\":\"MSDK_SCAN_LOGIN\"", "\"device_info\":");
    }
}
