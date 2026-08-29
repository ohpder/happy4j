package cc.superbaby.happy4j.infrastructure.auth.qq;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class QqQrLoginGatewayTest {
    @Test
    void ptqrtokenMatchesTencentHash() {
        assertThat(QqQrLoginGateway.qrToken("fb49015ce3be1e537bba431ac899d067"))
                .isEqualTo(64634350);
    }

    @Test
    void reproducesApkOpenSdkApplicationSign() {
        assertThat(QqQrLoginGateway.appSign("com.qqgame.hlddz",
                "5c101bee9d0f4cc50ae05f3736a44c84", "1787795598"))
                .isEqualTo("2ef97e8282b69f49e2ab59798052e04a");
    }

    @Test
    void parsesConfirmedCallbackWithExtraArgumentsAndEscapes() {
        var values = QqQrLoginGateway.parsePtuiCallback(
                "ptuiCB('0','0','https:\\/\\/ssl.ptlogin2.qq.com\\/check_sig?x=1\\x26y=2','0','登录成功！','nick\\'name', '');");
        assertThat(values).hasSize(7);
        assertThat(values.get(0)).isEqualTo("0");
        assertThat(values.get(2)).isEqualTo("https://ssl.ptlogin2.qq.com/check_sig?x=1&y=2");
        assertThat(values.get(5)).isEqualTo("nick'name");
    }

    @Test
    void rewritesOpenMobileCheckSigToCookieCompatibleHost() {
        URI input = URI.create("https://ssl.ptlogin2.openmobile.qq.com/check_sig?uin=1&s_url=https%3A%2F%2Fgraph.qq.com%2Fx%3Fa%3D1%26b%3D2#done");

        URI result = QqQrLoginGateway.normalizeTencentCallback(input);

        assertThat(result.toString())
                .isEqualTo("https://ssl.ptlogin2.qq.com/check_sig?uin=1&s_url=https%3A%2F%2Fgraph.qq.com%2Fx%3Fa%3D1%26b%3D2#done");
    }

    @Test
    void rewritesCurrentPtlogin4HttpCheckSigToCookieCompatibleHttpsHost() {
        URI input = URI.create("http://ptlogin4.openmobile.qq.com/check_sig?uin=1&s_url=http%3A%2F%2Fconnect.qq.com");

        URI result = QqQrLoginGateway.normalizeTencentCallback(input);

        assertThat(result.toString())
                .isEqualTo("https://ssl.ptlogin2.qq.com/check_sig?uin=1&s_url=http%3A%2F%2Fconnect.qq.com");
    }

    @Test
    void leavesOtherCallbackHostsUnchanged() {
        URI input = URI.create("https://graph.qq.com/oauth2.0/login_jump");
        assertThat(QqQrLoginGateway.normalizeTencentCallback(input)).isSameAs(input);
    }

    @Test
    void extractsExactXloginFromMobileAuthorizePage() {
        String page = "<script>var src = \"https\\x3A\\x2F\\x2Fxui.ptlogin2.qq.com\\x2Fcgi-bin\\x2Fxlogin"
                + "\\x3Fappid\\x3D716027609\\x26s_url\\x3Dhttp\\x253A\\x252F\\x252Fconnect.qq.com\";</script>";

        URI result = QqQrLoginGateway.extractAuthorizeSource(page);

        assertThat(result.getHost()).isEqualTo("xui.ptlogin2.qq.com");
        assertThat(result.getPath()).isEqualTo("/cgi-bin/xlogin");
        assertThat(result.getRawQuery()).contains("appid=716027609", "s_url=http%3A%2F%2Fconnect.qq.com");
    }

    @Test
    void buildsCurrentFlexOpenLoginDataFromRawXloginQuery() {
        URI xlogin = URI.create("https://xui.ptlogin2.qq.com/cgi-bin/xlogin?appid=716027609&s_url=http%3A%2F%2Fconnect.qq.com");

        assertThat(QqQrLoginGateway.openLoginData(xlogin))
                .isEqualTo("appid=716027609&s_url=http%3A%2F%2Fconnect.qq.com&pt_flex=1");
    }

    @Test
    void extractsTokenBeforeParsingBrowserToleratedRepeatedFragments() {
        String redirect = "[https://imgcache.qq.com/proxy.htm?#&t=1#&access\\_token=TOKEN123&openid=OPEN]"
                + "(https://imgcache.qq.com/proxy.htm?#&t=1#&access_token=TOKEN123&openid=OPEN"
                + "&pay_token=PAY123&key=PFKEY123)";

        assertThat(QqQrLoginGateway.tokenFrom(redirect)).contains("TOKEN123");
        assertThat(invokeFragmentValue(redirect, "pay_token")).contains("PAY123");
        assertThat(invokeFragmentValue(redirect, "key")).contains("PFKEY123");
        assertThat(QqQrLoginGateway.browserUri(redirect).toString())
                .contains("?#&t=1%23&access_token=TOKEN123");
    }

    private static java.util.Optional<String> invokeFragmentValue(String value, String name) {
        return QqQrLoginGateway.callbackValue(value, name);
    }
}
