package cc.superbaby.happy4j.infrastructure.auth.wechat;

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
 * Minimal MSDK v5 auth transport recovered from the APK's QuickJS module and
 * MiniMSDK native getURL implementation.  It obtains a WeChat DiffDev signature
 * and exchanges the resulting code without embedding a WeChat AppSecret.
 */
@Component
final class MsdkWechatAuthClient {
    private static final String SCAN_PATH = "/v2/auth/scan_login";
    private static final String LOGIN_PATH = "/v2/auth/login";
    private final Happy4jProperties.Auth.Wechat config;
    private final HttpClient http;
    private final ObjectMapper json;
    private final MsdkDeviceContext device;

    MsdkWechatAuthClient(Happy4jProperties properties, HttpClient http, ObjectMapper json,
                         MsdkDeviceContext device) {
        this.config = properties.auth().wechat();
        this.http = http;
        this.json = json;
        this.device = device;
    }

    QrSignature issueQrSignature() throws Exception {
        JsonNode root = post(SCAN_PATH, "{}");
        String signature = root.path("sig").asString();
        String nonce = root.path("random").asString();
        String timestamp = root.path("timestamp").asString();
        if (signature.isBlank() || nonce.isBlank() || timestamp.isBlank()) {
            throw new IllegalStateException("MSDK 微信二维码签名字段不完整");
        }
        return new QrSignature(nonce, timestamp, signature);
    }

    PlatformCredential exchangeCode(String code) throws Exception {
        JsonNode root = post(LOGIN_PATH, qrLoginBody(code, json, device));
        String openId = text(root, "openid", "open_id", "channel_openid", "channelOpenID", "openID");
        String token = text(root, "token", "access_token", "accessToken", "channel_access_token");
        if (openId.isBlank() || token.isBlank()) {
            throw new IllegalStateException("MSDK 微信登录响应缺少 openid 或 token");
        }
        return new PlatformCredential(PlatformCredential.Provider.WECHAT, openId, token,
                text(root, "pf"), text(root, "pf_key", "pfKey"), text(root, "pay_token", "payToken"));
    }

    static String qrLoginBody(String code, ObjectMapper json, MsdkDeviceContext device) throws Exception {
        Map<String, Object> channelInfo = new LinkedHashMap<>();
        channelInfo.put("code", code);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("channel_dis", "WeChat");
        body.put("channel_info", channelInfo);
        // MSDKQRLoginManager adds {QRCode:true}; LoginManager converts that
        // scene to the backend MSDK_SCAN_LOGIN discriminator.
        body.put("QRCode", true);
        body.put("login_type", "MSDK_SCAN_LOGIN");
        device.enrichQrLogin(body);
        return json.writeValueAsString(body);
    }

    private JsonNode post(String path, String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String sequence = MsdkRequestCodec.nextSequence(timestamp);
        String canonical = canonicalQuery(config.msdkChannelId(), config.msdkGameId(), config.msdkOs(),
                sequence, timestamp, "", config.msdkVersion());
        String signature = requestSignature(path, canonical, config.msdkSdkKey(), body);
        URI endpoint = URI.create(trimSlash(config.msdkBaseUrl()) + path + '?' + canonical + "&sig=" + signature);
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json;charset=utf-8")
                .header("Accept", "application/json")
                .header("User-Agent", "MSDK/5.38.102 happy4j")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("MSDK HTTP " + response.statusCode());
        }
        JsonNode root = json.readTree(response.body());
        int result = root.path("ret").asInt(-1);
        if (result != 0) {
            throw new IllegalStateException("MSDK ret=" + result + ": " + root.path("msg").asString());
        }
        if (!sequence.equals(root.path("seq").asString())) {
            throw new IllegalStateException("MSDK 响应 seq 与请求不一致");
        }
        return root;
    }

    static String canonicalQuery(int channelId, int gameId, int os, String sequence,
                                 long timestamp, String lang, String version) {
        return MsdkRequestCodec.canonical(channelId, gameId, os, sequence, timestamp, lang, version);
    }

    /**
     * MiniMSDK getURL: MD5(path + '?' + canonical + sdkKey + exactBody) + '1'.
     */
    static String requestSignature(String path, String canonical, String sdkKey, String body) {
        return MsdkRequestCodec.signature(path, canonical, sdkKey, body);
    }

    private static JsonNode find(JsonNode node, String... names) {
        if (node == null) return null;
        for (String name : names) {
            JsonNode direct = node.get(name);
            if (direct != null && !direct.isNull()) return direct;
        }
        if (node.isContainer()) {
            for (JsonNode child : node) {
                JsonNode found = find(child, names);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String text(JsonNode root, String... names) {
        JsonNode value = find(root, names);
        return value == null ? "" : value.asString("");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    record QrSignature(String nonce, String timestamp, String signature) {
    }
}
