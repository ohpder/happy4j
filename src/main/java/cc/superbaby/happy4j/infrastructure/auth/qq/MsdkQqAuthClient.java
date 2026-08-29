package cc.superbaby.happy4j.infrastructure.auth.qq;

import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.PlatformCredential;
import cc.superbaby.happy4j.infrastructure.auth.msdk.MsdkDeviceContext;
import cc.superbaby.happy4j.infrastructure.auth.msdk.MsdkRequestCodec;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exchanges QQ OpenSDK QR fields for the MSDK v5 credential consumed by TSDK.
 */
@Component
final class MsdkQqAuthClient {
    private static final String LOGIN_PATH = "/v2/auth/login";
    private final Happy4jProperties.Auth.Qq qq;
    private final Happy4jProperties.Auth.Wechat msdk;
    private final HttpClient http;
    private final ObjectMapper json;
    private final MsdkDeviceContext device;

    MsdkQqAuthClient(Happy4jProperties properties, HttpClient http, ObjectMapper json,
                     MsdkDeviceContext device) {
        this.qq = properties.auth().qq();
        this.msdk = properties.auth().wechat();
        this.http = http;
        this.json = json;
        this.device = device;
    }

    PlatformCredential exchange(String openId, String accessToken, String payToken, String openSdkKey) throws Exception {
        Map<String, Object> channelInfo = new LinkedHashMap<>();
        channelInfo.put("openid", openId);
        channelInfo.put("access_token", accessToken);
        channelInfo.put("pay_token", value(payToken));
        channelInfo.put("key", value(openSdkKey));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel_dis", "QQ");
        body.put("channel_info", channelInfo);
        body.put("QRCode", true);
        body.put("login_type", "MSDK_SCAN_LOGIN");
        device.enrichQrLogin(body);
        String bodyText = json.writeValueAsString(body);

        long timestamp = Instant.now().getEpochSecond();
        String sequence = MsdkRequestCodec.nextSequence(timestamp);
        String canonical = MsdkRequestCodec.canonical(qq.msdkChannelId(), msdk.msdkGameId(), msdk.msdkOs(),
                sequence, timestamp, "", msdk.msdkVersion());
        String signature = MsdkRequestCodec.signature(LOGIN_PATH, canonical, msdk.msdkSdkKey(), bodyText);
        URI endpoint = URI.create(trimSlash(msdk.msdkBaseUrl()) + LOGIN_PATH + '?' + canonical + "&sig=" + signature);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json;charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "MSDK/5.38.102 happy4j")
                .POST(HttpRequest.BodyPublishers.ofString(bodyText, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2)
            throw new IllegalStateException("MSDK QQ HTTP " + response.statusCode());
        JsonNode root = json.readTree(response.body());
        int result = root.path("ret").asInt(-1);
        if (result != 0)
            throw new IllegalStateException("MSDK QQ ret=" + result + ": " + root.path("msg").asString());
        if (!sequence.equals(root.path("seq").asString()))
            throw new IllegalStateException("MSDK QQ 响应 seq 与请求不一致");
        String msdkOpenId = text(root, "openid", "open_id", "uid");
        String token = text(root, "token", "access_token", "channel_access_token");
        if (msdkOpenId.isBlank() || token.isBlank())
            throw new IllegalStateException("MSDK QQ 响应缺少 openid/token");
        return new PlatformCredential(PlatformCredential.Provider.QQ, msdkOpenId, token,
                text(root, "pf"), text(root, "pf_key", "pfKey"),
                defaultText(root, payToken, "pay_token", "payToken"));
    }

    static String qqLoginBody(String openId, String accessToken, String payToken, String key,
                              ObjectMapper json, MsdkDeviceContext device) throws Exception {
        Map<String, Object> channel = new LinkedHashMap<>();
        channel.put("openid", openId);
        channel.put("access_token", accessToken);
        channel.put("pay_token", value(payToken));
        channel.put("key", value(key));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel_dis", "QQ");
        body.put("channel_info", channel);
        body.put("QRCode", true);
        body.put("login_type", "MSDK_SCAN_LOGIN");
        device.enrichQrLogin(body);
        return json.writeValueAsString(body);
    }

    private static String text(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.isNull() && !value.asString().isBlank())
                return value.asString();
        }
        return "";
    }

    private static String defaultText(JsonNode root, String fallback, String... names) {
        String value = text(root, names);
        return value.isBlank() ? value(fallback) : value;
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
