package cc.superbaby.happy4j.infrastructure.auth.game;

import cc.superbaby.happy4j.application.port.GameLoginPort;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.PlatformCredential;
import cc.superbaby.happy4j.protocol.wire.QqTeaCipher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Selects the APK domestic/overseas SafeLogin transport and maps live credentials.
 */
@Component
public final class SafeLoginHttpGateway implements GameLoginPort {
    private static final Logger LOG = LoggerFactory.getLogger(SafeLoginHttpGateway.class);
    private static final String UA = "TSDK/3.6.306 hlddz/8.056.016 Android/35";
    private final HttpClient http;
    private final ObjectMapper json;
    private final GCloudSafeLoginRequestFactory gcloudRequests;
    private final LegacySafeLoginRequestFactory legacyRequests;
    private final String protocol;

    public SafeLoginHttpGateway(HttpClient http, ObjectMapper json, Happy4jProperties properties) throws IOException {
        this.http = http;
        this.json = json;
        this.gcloudRequests = new GCloudSafeLoginRequestFactory(properties.game());
        this.legacyRequests = new LegacySafeLoginRequestFactory(properties, json);
        this.protocol = properties.game().safeLoginProtocol();
    }

    @Override
    public GameCredential login(PlatformCredential platform) {
        try {
            if ("gcloud".equalsIgnoreCase(protocol)) return loginGCloud(platform);
            return loginLegacy(platform);
        } catch (IOException e) {
            throw new IllegalStateException("SafeLogin 网络失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SafeLogin 被中断", e);
        }
    }

    private GameCredential loginLegacy(PlatformCredential platform) throws IOException, InterruptedException {
        LegacySafeLoginRequestFactory.Request safeLogin = legacyRequests.create(platform);
        HttpRequest request = HttpRequest.newBuilder(safeLogin.endpoint()).timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Referer", safeLogin.referer())
                .header("Cookie", safeLogin.cookie())
                .header("Content-Type", LegacySafeLoginRequestFactory.CONTENT_TYPE)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(safeLogin.body())).build();
        JsonNode root = send(request);
        requireSuccess(root, "legacy");
        byte[] enKey = decryptLegacy(root, safeLogin.randomKey(), "enckey", "enkey", "strEnkey");
        byte[] sessionTicket = decryptLegacy(root, safeLogin.randomKey(), "skey", "st", "ST", "ticket", "ucST");
        return credential(root, platform, safeLogin.randomKey(), sessionTicket,
                new String(enKey, StandardCharsets.ISO_8859_1), safeLogin.deviceId());
    }

    private GameCredential loginGCloud(PlatformCredential platform) throws IOException, InterruptedException {
        GCloudSafeLoginRequestFactory.Request safeLogin = gcloudRequests.create(platform);
        HttpRequest request = HttpRequest.newBuilder(safeLogin.endpoint()).timeout(Duration.ofSeconds(20))
                .header("User-Agent", UA)
                .header("Content-Type", GCloudSafeLoginRequestFactory.CONTENT_TYPE)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(safeLogin.body())).build();
        JsonNode root = send(request);
        requireSuccess(root, "gcloud");
        byte[] encryptionKey = base64Bytes(root, "enckey", "enkey", "strEnkey");
        if (encryptionKey.length != 16) {
            throw new IllegalStateException("SafeLogin 响应 enckey 长度异常: " + encryptionKey.length);
        }
        return credential(root, platform, encryptionKey,
                base64Bytes(root, "skey", "IndentBuf", "st", "ST", "ticket", "ucST"),
                new String(encryptionKey, StandardCharsets.ISO_8859_1), "");
    }

    private JsonNode send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        String contentType = response.headers().firstValue("content-type").orElse("");
        Charset charset = contentType.toLowerCase().contains("gb2312")
                ? Charset.forName("GB2312") : StandardCharsets.UTF_8;
        String responseBody = new String(response.body(), charset);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("SafeLogin HTTP " + response.statusCode() + ": " + responseBody);
        }
        return json.readTree(responseBody);
    }

    private static void requireSuccess(JsonNode root, String transport) {
        int result = integer(root, "result", "ret", "code", "nResult");
        if (result != 0)
            throw new IllegalStateException("SafeLogin[" + transport + "] result=" + result + ": "
                    + text(root, "resultstr", "errinfo", "msg", "strResult"));
    }

    private GameCredential credential(JsonNode root, PlatformCredential platform, byte[] randomKey,
                                      byte[] sessionTicket, String enKey, String deviceId) {
        Map<String, Object> raw = new LinkedHashMap<>(
                json.convertValue(root, new TypeReference<Map<String, Object>>() {
                }));
        if (deviceId != null && !deviceId.isBlank()) raw.put("deviceID", deviceId);
        long uin = resolveGameUin(root);
        long beans = unsigned(root, "bean", "beans", "llBeanNum");
        long diamonds = unsigned(root, "diamond", "diamonds", "llDiamondNum");
        LOG.info("SafeLogin credential provider={} resultUin={} myuin={} selectedUin={} stBytes={} enKeyBytes={}",
                platform.provider(), unsigned(root, "uin"), unsigned(root, "myuin"), uin,
                sessionTicket.length, enKey.getBytes(StandardCharsets.ISO_8859_1).length);
        // SafeLogin does not carry the authoritative wallet. These values are only a bootstrap;
        // HappyDdzClientService replaces them after Lotus connects through QueryInfoByBitflag.
        LOG.info("SafeLogin bootstrap wallet fields beans={} diamonds={}; live balance follows over Lotus gRPC",
                beans, diamonds);
        return new GameCredential(uin,
                unsigned(root, "playerid", "player_id", "unPlayerId"), platform.provider(), randomKey,
                sessionTicket, enKey,
                defaultText(root, platform.openId(), "openid", "open_id", "strOpenId"),
                defaultText(root, platform.accessToken(), "openkey", "open_key", "strAccessToken"),
                defaultText(root, platform.payToken(), "paytoken", "pay_token", "strPayToken"),
                defaultText(root, platform.pf(), "pf", "strPf"),
                defaultText(root, platform.pfKey(), "pfkey", "pf_key", "strPfKey"),
                beans, diamonds, raw);
    }

    /**
     * Legacy success replies expose a placeholder uin=0 and the actual game UIN as myuin.
     */
    static long resolveGameUin(JsonNode root) {
        for (String name : new String[]{"gameuin", "game_uin", "unGameUin", "myuin", "uin"}) {
            long value = unsigned(root, name);
            if (value > 0) return value;
        }
        return 0;
    }

    private static byte[] decryptLegacy(JsonNode root, byte[] randomKey, String... names) {
        byte[] encrypted = base64Bytes(root, names);
        if (encrypted.length == 0) return encrypted;
        try {
            return QqTeaCipher.decrypt(encrypted, randomKey);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("SafeLogin legacy 字段解密失败: " + names[0], failure);
        }
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

    private static String defaultText(JsonNode root, String fallback, String... names) {
        String value = text(root, names);
        return value.isBlank() ? fallback : value;
    }

    private static int integer(JsonNode root, String... names) {
        JsonNode value = find(root, names);
        return value == null ? 0 : value.asInt();
    }

    private static long unsigned(JsonNode root, String... names) {
        JsonNode value = find(root, names);
        return value == null ? 0 : value.asLong();
    }

    private static byte[] bytes(JsonNode root, String... names) {
        String value = text(root, names).trim();
        if (value.isBlank()) return new byte[0];
        if (value.matches("(?i)[0-9a-f]+") && value.length() % 2 == 0)
            return HexFormat.of().parseHex(value);
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return value.getBytes(StandardCharsets.ISO_8859_1);
        }
    }

    private static byte[] base64Bytes(JsonNode root, String... names) {
        String value = text(root, names).trim();
        if (value.isBlank()) return new byte[0];
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException ignored) {
            return bytes(root, names);
        }
    }
}
