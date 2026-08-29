package cc.superbaby.happy4j.infrastructure.auth.game;

import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.PlatformCredential;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the optional overseas MSDK v5 → TSDK GCloud SafeLogin request.
 */
final class GCloudSafeLoginRequestFactory {
    static final String PATH = "/cgi-bin/CommonMobileCGI/Common_SafeLogin_GCloud";
    static final String CONTENT_TYPE = "application/x-www-form-urlencoded; charset=UTF-8";
    private final Happy4jProperties.Game config;

    GCloudSafeLoginRequestFactory(Happy4jProperties.Game config) {
        this.config = config;
    }

    Request create(PlatformCredential credential) {
        int accountType = credential.provider() == PlatformCredential.Provider.WECHAT ? 1 : 2;
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("clienttype", Integer.toString(config.clientType()));
        fields.put("accounttype", Integer.toString(accountType));
        fields.put("clientversion", Integer.toString(config.clientVersion()));
        fields.put("gameid", Integer.toString(config.id()));
        fields.put("openid", credential.openId());
        fields.put("token", credential.accessToken());
        String baseUrl = credential.provider() == PlatformCredential.Provider.WECHAT
                ? config.wechatLoginBaseUrl() : config.loginBaseUrl();
        return new Request(URI.create(trimSlash(baseUrl) + PATH), formEncode(fields));
    }

    static String formEncode(Map<String, String> fields) {
        return fields.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("SafeLogin base URL 为空");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    record Request(URI endpoint, String body) {
    }
}
