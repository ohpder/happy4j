package cc.superbaby.happy4j.infrastructure.auth.game;

import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.PlatformCredential;

import org.springframework.core.io.ClassPathResource;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exact domestic TSDK 3.6 SafeLogin request, including referer/cookie gates.
 */
final class LegacySafeLoginRequestFactory {
    // CCurlHttpClient::BuildEasyCurl (libTSDK4CSharp-il2cpp.so 0x27d89c) never calls
    // curl_slist_append with a Content-Type override for this CGI, so libcurl falls back
    // to its default even though the body text is JSON; the CGI gateway keys off that.
    static final String CONTENT_TYPE = "application/x-www-form-urlencoded";
    // QR-scan login dispatches through CLoginTransaction::RequestPCLogin (accountType 4,
    // "plattype"/"terminaltype" body shape), not RequestQQLogin's accountType-2/"...ByOpenID" CGI —
    // confirmed against a live device capture (TSDK LOGIN_LOG) of a successful QQ scan login.
    private static final String QQ_PATH = "/cgi-bin/CommonMobileCGI/Common_MobileQQ_SafeLogin";
    private static final String WECHAT_PATH = "/cgi-bin/CommonMobileCGI/Common_WeChat_SafeLogin";
    private static final int QQ_ACCOUNT_TYPE = 4;
    private static final int WECHAT_ACCOUNT_TYPE = 1;
    private static final char[] RANDOM_ALPHABET =
            "_0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_".toCharArray();

    private final Happy4jProperties properties;
    private final Happy4jProperties.Game game;
    private final ObjectMapper json;
    private final SafeLoginCrypto crypto;
    private final SecureRandom random = new SecureRandom();
    private final String deviceId;

    LegacySafeLoginRequestFactory(Happy4jProperties properties, ObjectMapper json) throws IOException {
        this.properties = properties;
        this.game = properties.game();
        this.json = json;
        this.crypto = new SafeLoginCrypto(new ClassPathResource("hlddz/pubkey.key")
                .getContentAsString(StandardCharsets.US_ASCII));
        this.deviceId = randomText(25);
    }

    Request create(PlatformCredential credential) throws IOException {
        boolean wechat = credential.provider() == PlatformCredential.Provider.WECHAT;
        int accountType = wechat ? WECHAT_ACCOUNT_TYPE : QQ_ACCOUNT_TYPE;
        String randomKeyText = randomText(16);
        byte[] randomKey = randomKeyText.getBytes(StandardCharsets.US_ASCII);
        String access = "a1=" + credential.openId() + "&a2=" + credential.accessToken() + "&a3=" + randomKeyText;

        // Field set/order matches CLoginTransaction::RequestPCLogin's strBody for both
        // providers (only accounttype/plattype and the CGI path differ) — verified against
        // a live device capture of a successful QQ scan login.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("a0", crypto.encrypt(access));
        // CLoginTransaction::EncryptAccess (libTSDK4CSharp-il2cpp.so 0x2ffb34) always RSA-encrypts
        // the (empty) custom-info block and feeds that ciphertext into b0, for every provider.
        body.put("b0", crypto.encrypt(""));
        body.put("plattype", accountType);
        body.put("terminaltype", game.clientType());
        body.put("retry", 0);
        body.put("version", 2);
        body.put("deviceID", deviceId);
        body.put("gameid", game.id());
        body.put("is_msdk_v5", true);

        String baseUrl = wechat ? game.wechatLoginBaseUrl() : game.loginBaseUrl();
        URI endpoint = URI.create(trimSlash(baseUrl) + (wechat ? WECHAT_PATH : QQ_PATH));
        String referer = "http://emptyreferer.qqgame.qq.com/client/" + game.id()
                + '/' + game.clientType() + "/0";
        return new Request(endpoint, json.writeValueAsString(body), referer,
                cookie(credential, accountType, wechat), randomKey, deviceId);
    }

    private String cookie(PlatformCredential credential, int accountType, boolean wechat) {
        String appId = wechat ? properties.auth().wechat().appId()
                : Integer.toString(properties.auth().qq().thirdAppId());
        return "openid=" + credential.openId()
                + ";accesstoken=" + credential.accessToken()
                + ";uin=0;webskey="
                + ";accounttype=" + accountType
                + ";clienttype=" + game.clientType()
                + ";clientip=;gameid=" + game.id()
                + ";appid=" + appId
                + ";channelid=" + game.channelId()
                + ";osversion=" + game.osVersion()
                + ";gameversion=" + game.clientVersion()
                + ";systemversion=" + game.clientVersion()
                + ";pfkey=pfkey;pf=" + value(credential.pf())
                + ";openkey=" + credential.accessToken()
                + ";paytoken=" + value(credential.payToken())
                + ";skey=";
    }

    private String randomText(int length) {
        char[] value = new char[length];
        for (int i = 0; i < value.length; i++) value[i] = RANDOM_ALPHABET[random.nextInt(64)];
        return new String(value);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("SafeLogin base URL 为空");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    record Request(URI endpoint, String body, String referer, String cookie, byte[] randomKey,
                   String deviceId) {
        Request {
            randomKey = randomKey.clone();
            deviceId = deviceId == null ? "" : deviceId;
        }

        @Override
        public byte[] randomKey() {
            return randomKey.clone();
        }
    }
}
