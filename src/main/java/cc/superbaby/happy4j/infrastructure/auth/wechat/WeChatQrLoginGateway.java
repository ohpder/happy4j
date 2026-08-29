package cc.superbaby.happy4j.infrastructure.auth.wechat;

import cc.superbaby.happy4j.application.port.QrAuthPort;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.PlatformCredential;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WeChat OpenSDK DiffDev OAuth, matching the APK's bundled SDK implementation.
 */
@Component
public final class WeChatQrLoginGateway implements QrAuthPort {
    private static final String QR_ENDPOINT = "https://open.weixin.qq.com/connect/sdk/qrconnect";
    private static final String POLL_ENDPOINT = "https://long.open.weixin.qq.com/connect/l/qrconnect";
    private final Happy4jProperties.Auth.Wechat config;
    private final HttpClient http;
    private final ObjectMapper json;
    private final MsdkWechatAuthClient msdk;
    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    public WeChatQrLoginGateway(Happy4jProperties properties, HttpClient http, ObjectMapper json,
                                MsdkWechatAuthClient msdk) {
        this.config = properties.auth().wechat();
        this.http = http;
        this.json = json;
        this.msdk = msdk;
    }

    @Override
    public String provider() {
        return "wechat";
    }

    @Override
    public QrChallenge start() {
        try {
            String nonce;
            String timestamp;
            String signature;
            if (!blank(config.sdkTicket()) || !blank(config.appSecret())) {
                String ticket = blank(config.sdkTicket()) ? fetchSdkTicket() : config.sdkTicket();
                nonce = UUID.randomUUID().toString().replace("-", "");
                timestamp = Long.toString(Instant.now().getEpochSecond());
                signature = sha1("appid=" + config.appId() + "&noncestr=" + nonce
                        + "&sdk_ticket=" + ticket + "&timestamp=" + timestamp);
            } else {
                MsdkWechatAuthClient.QrSignature issued = msdk.issueQrSignature();
                nonce = issued.nonce();
                timestamp = issued.timestamp();
                signature = issued.signature();
            }
            String query = form(Map.of("appid", config.appId(), "noncestr", nonce,
                    "timestamp", timestamp, "scope", config.scope(), "signature", signature));
            HttpResponse<String> response = get(URI.create(QR_ENDPOINT + '?' + query), Duration.ofSeconds(20));
            JsonNode root = json.readTree(response.body());
            if (root.path("errcode").asInt(-1) != 0) {
                throw new IllegalStateException("微信二维码返回 " + root.path("errcode").asInt() + ": " + root.path("errmsg").asString());
            }
            byte[] image = Base64.getMimeDecoder().decode(root.path("qrcode").path("qrcodebase64").asString());
            String uuid = root.path("uuid").asString();
            if (image.length == 0 || uuid.isBlank())
                throw new IllegalStateException("微信二维码响应缺少图片或 uuid");
            String id = UUID.randomUUID().toString();
            challenges.put(id, new Challenge(uuid, image, Instant.now().plusSeconds(300)));
            return new QrChallenge(id, image, 300);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("创建微信扫码会话被中断", failure);
        } catch (Exception failure) {
            if (failure instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("创建微信扫码会话失败", failure);
        }
    }

    @Override
    public QrPollResult poll(String challengeId) {
        Challenge challenge = required(challengeId);
        if (Instant.now().isAfter(challenge.expiresAt)) {
            challenges.remove(challengeId);
            return new QrPollResult(QrPollResult.Status.EXPIRED, "微信二维码已过期", null);
        }
        try {
            String suffix = challenge.last == 0 ? "" : "&last=" + challenge.last;
            URI uri = URI.create(POLL_ENDPOINT + "?f=json&uuid=" + enc(challenge.uuid) + suffix);
            JsonNode root = json.readTree(get(uri, Duration.ofSeconds(65)).body());
            int code = root.path("wx_errcode").asInt(500);
            challenge.last = code;
            return switch (code) {
                case 408 -> new QrPollResult(QrPollResult.Status.WAITING, "等待微信扫码", null);
                case 404 -> new QrPollResult(QrPollResult.Status.SCANNED, "已扫码，请在微信确认", null);
                case 405 -> complete(challengeId, root.path("wx_code").asString());
                case 402 -> new QrPollResult(QrPollResult.Status.EXPIRED, "微信二维码已过期", null);
                case 403 -> new QrPollResult(QrPollResult.Status.FAILED, "已在微信取消", null);
                default -> new QrPollResult(QrPollResult.Status.FAILED, "微信轮询返回 " + code, null);
            };
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            return new QrPollResult(QrPollResult.Status.FAILED, "微信轮询被中断", null);
        } catch (Exception failure) {
            return new QrPollResult(QrPollResult.Status.FAILED, "微信轮询失败: " + failure.getMessage(), null);
        }
    }

    private QrPollResult complete(String id, String code) throws Exception {
        if (code.isBlank())
            return new QrPollResult(QrPollResult.Status.FAILED, "微信确认响应缺少 code", null);
        if (blank(config.appSecret())) return completeWithMsdk(id, code);
        String query = form(Map.of("appid", config.appId(), "secret", config.appSecret(), "code", code,
                "grant_type", "authorization_code"));
        JsonNode root = json.readTree(get(URI.create("https://api.weixin.qq.com/sns/oauth2/access_token?" + query), Duration.ofSeconds(20)).body());
        if (root.has("errcode")) return new QrPollResult(QrPollResult.Status.FAILED,
                "微信 token 返回 " + root.path("errcode").asInt() + ": " + root.path("errmsg").asString(), null);
        String openId = root.path("openid").asString();
        String token = root.path("access_token").asString();
        if (openId.isBlank() || token.isBlank())
            return new QrPollResult(QrPollResult.Status.FAILED, "微信 token 响应字段不完整", null);
        challenges.remove(id);
        return new QrPollResult(QrPollResult.Status.CONFIRMED, "微信扫码登录成功",
                new PlatformCredential(PlatformCredential.Provider.WECHAT, openId, token, "", "", ""));
    }

    private QrPollResult completeWithMsdk(String id, String code) {
        try {
            PlatformCredential credential = msdk.exchangeCode(code);
            challenges.remove(id);
            return new QrPollResult(QrPollResult.Status.CONFIRMED, "微信扫码登录成功", credential);
        } catch (Exception failure) {
            return new QrPollResult(QrPollResult.Status.FAILED, "微信 MSDK 登录失败: " + failure.getMessage(), null);
        }
    }

    private String fetchSdkTicket() throws Exception {
        if (blank(config.appSecret())) throw new IllegalStateException("微信 AppSecret 为空");
        String tokenQuery = form(Map.of("grant_type", "client_credential", "appid", config.appId(), "secret", config.appSecret()));
        JsonNode token = json.readTree(get(URI.create("https://api.weixin.qq.com/cgi-bin/token?" + tokenQuery), Duration.ofSeconds(20)).body());
        String accessToken = token.path("access_token").asString();
        if (accessToken.isBlank())
            throw new IllegalStateException("微信 access_token 返回 " + token.path("errcode").asInt()
                    + ": " + token.path("errmsg").asString());
        JsonNode ticket = json.readTree(get(URI.create("https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token="
                + enc(accessToken) + "&type=2"), Duration.ofSeconds(20)).body());
        if (ticket.path("errcode").asInt(-1) != 0 || ticket.path("ticket").asString().isBlank()) {
            throw new IllegalStateException("微信 sdk_ticket 返回 " + ticket.path("errcode").asInt() + ": " + ticket.path("errmsg").asString());
        }
        return ticket.path("ticket").asString();
    }

    @Override
    public byte[] image(String challengeId) {
        return required(challengeId).image.clone();
    }

    static String signature(String appId, String nonce, String ticket, String timestamp) {
        return sha1("appid=" + appId + "&noncestr=" + nonce + "&sdk_ticket=" + ticket + "&timestamp=" + timestamp);
    }

    private HttpResponse<String> get(URI uri, Duration timeout) throws Exception {
        return http.send(HttpRequest.newBuilder(uri).timeout(timeout).header("User-Agent", "MicroMessenger/OpenSDK").GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Challenge required(String id) {
        Challenge value = challenges.get(id);
        if (value == null) throw new IllegalArgumentException("微信扫码会话不存在或已结束");
        return value;
    }

    private static String sha1(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream().map(item -> enc(item.getKey()) + '=' + enc(item.getValue()))
                .reduce((left, right) -> left + '&' + right).orElse("");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static final class Challenge {
        final String uuid;
        final byte[] image;
        final Instant expiresAt;
        volatile int last;

        Challenge(String uuid, byte[] image, Instant expiresAt) {
            this.uuid = uuid;
            this.image = image.clone();
            this.expiresAt = expiresAt;
        }
    }
}
