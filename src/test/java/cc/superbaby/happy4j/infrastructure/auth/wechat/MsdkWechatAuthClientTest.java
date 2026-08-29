package cc.superbaby.happy4j.infrastructure.auth.wechat;

import org.junit.jupiter.api.Test;

import cc.superbaby.happy4j.infrastructure.auth.msdk.MsdkDeviceContext;

import static org.assertj.core.api.Assertions.assertThat;

class MsdkWechatAuthClientTest {
    @Test
    void buildsCanonicalQueryInMiniMsdkOrder() {
        assertThat(MsdkWechatAuthClient.canonicalQuery(1, 28222, 1,
                "1787795598-1234567890-123456-9876543210", 1787795598L, "", ""))
                .isEqualTo("channelid=1&from=msdkpix&gameid=28222&lang=&os=1"
                        + "&seq=1787795598-1234567890-123456-9876543210&ts=1787795598&version=");
    }

    @Test
    void matchesRecoveredMiniMsdkGetUrlSignature() {
        String canonical = "channelid=1&from=msdkpix&gameid=28222&lang=&os=1"
                + "&seq=1787795598-1234567890-123456-9876543210&ts=1787795598&version=";
        assertThat(MsdkWechatAuthClient.requestSignature("/v2/auth/scan_login", canonical,
                "2def33340d76ea3d2db80ef946aded6b", "{}"))
                .isEqualTo("7408d1da8d4bd4d5466fcfbf5208bb261");
    }

    @Test
    void qrLoginBodyKeepsMsdkScanDiscriminator() throws Exception {
        String body = MsdkWechatAuthClient.qrLoginBody("CODE", new tools.jackson.databind.ObjectMapper(),
                new MsdkDeviceContext());
        assertThat(body).contains("\"channel_dis\":\"WeChat\"", "\"code\":\"CODE\"",
                "\"QRCode\":true", "\"login_type\":\"MSDK_SCAN_LOGIN\"",
                "\"device_info\":", "\"uuid\":", "\"msdk_scenes\":\"QRCode\"");
    }
}
