package cc.superbaby.happy4j.infrastructure.auth.qq;

import cc.superbaby.happy4j.application.port.QrAuthPort;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.PlatformCredential;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * QQ Connect QR OAuth implemented as a stateful server-side browser session.
 */
@Component
public final class QqQrLoginGateway implements QrAuthPort {
    private static final String UA = "Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36";
    private static final Pattern OPEN_ID = Pattern.compile("\\\"openid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("(?:access_token=|\\\"access_token\\\"\\s*:\\s*\\\")([^&\\\"'<>\\s]+)");
    private static final Pattern REDIRECT_URL = Pattern.compile("(?:(?:location(?:\\.href)?|url)\\s*[=:]\\s*['\\\"])([^'\\\"]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern AUTHORIZE_SRC = Pattern.compile("var\\s+src\\s*=\\s*[\"']([^\"']+)[\"']");
    private final Happy4jProperties.Auth.Qq config;
    private final MsdkQqAuthClient msdk;
    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    public QqQrLoginGateway(Happy4jProperties properties, MsdkQqAuthClient msdk) {
        this.config = properties.auth().qq();
        this.msdk = msdk;
    }

    @Override
    public String provider() {
        return "qq";
    }

    @Override
    public QrChallenge start() {
        try {
            CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
            HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                    .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
            String authTime = Long.toString(Instant.now().getEpochSecond());
            URI authorize = URI.create("https://openmobile.qq.com/oauth2.0/m_authorize?" + params(Map.ofEntries(
                    Map.entry("format", "json"),
                    Map.entry("status_os", config.statusOs()),
                    Map.entry("status_machine", Base64.getEncoder().encodeToString(
                            config.statusMachine().getBytes(StandardCharsets.UTF_8))),
                    Map.entry("status_version", Integer.toString(config.statusVersion())),
                    Map.entry("scope", "all"), Map.entry("client_id", Integer.toString(config.thirdAppId())),
                    Map.entry("pf", "openmobile_android"), Map.entry("response_type", "token"),
                    Map.entry("sign", appSign(config.packageName(), config.signatureMd5(), authTime)),
                    Map.entry("time", authTime),
                    Map.entry("redirect_uri", "auth://tauth.qq.com/"), Map.entry("cancel_display", "1"),
                    Map.entry("switch", "1"), Map.entry("compat_v", "1"), Map.entry("style", "qr"),
                    Map.entry("display", "mobile"), Map.entry("sdkp", "a"), Map.entry("sdkv", "3.5.17"),
                    Map.entry("show_download_ui", "true")
            )));
            String authorizePage = get(client, authorize).body();
            URI xlogin = extractAuthorizeSource(authorizePage);
            get(client, xlogin);
            String openLoginData = openLoginData(xlogin);
            String loginSig = cookie(cookies, "pt_login_sig").orElse("");
            URI qrUri = URI.create("https://ssl.ptlogin2.qq.com/ptqrshow?" + params(Map.of(
                    "appid", Integer.toString(config.appId()), "e", "2", "l", "M", "s", "3",
                    "d", "72", "v", "4", "t", Double.toString(Math.random()),
                    "daid", Integer.toString(config.daid()), "pt_3rd_aid", Integer.toString(config.thirdAppId()))));
            HttpResponse<byte[]> qr = getBytes(client, qrUri);
            if (qr.statusCode() / 100 != 2 || qr.body().length < 100) {
                throw new IllegalStateException("QQ 二维码接口返回 HTTP " + qr.statusCode());
            }
            String qrSig = cookie(cookies, "qrsig").orElseThrow(() -> new IllegalStateException("QQ 响应缺少 qrsig"));
            String id = UUID.randomUUID().toString();
            challenges.put(id, new Challenge(client, cookies, qr.body(), qrSig, loginSig,
                    openLoginData, Instant.now().plusSeconds(120)));
            return new QrChallenge(id, qr.body(), 120);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("创建 QQ 扫码会话失败", e);
        } catch (IOException e) {
            throw new IllegalStateException("创建 QQ 扫码会话失败", e);
        }
    }

    @Override
    public QrPollResult poll(String challengeId) {
        Challenge challenge = required(challengeId);
        if (Instant.now().isAfter(challenge.expiresAt)) {
            challenges.remove(challengeId);
            return new QrPollResult(QrPollResult.Status.EXPIRED, "二维码已过期", null);
        }
        try {
            long millis = System.currentTimeMillis();
            Map<String, String> query = new HashMap<>();
            query.put("u1", "http://connect.qq.com");
            query.put("ptqrtoken", Integer.toString(qrToken(challenge.qrSig)));
            query.put("ptredirect", "0");
            query.put("h", "1");
            query.put("t", "1");
            query.put("g", "1");
            query.put("from_ui", "1");
            query.put("ptlang", "2052");
            query.put("action", "0-0-" + millis);
            query.put("type", "1");
            query.put("device", "2");
            query.put("ptopt", "1");
            query.put("pt_openlogin_data", challenge.openLoginData);
            query.put("js_ver", "22080914");
            query.put("js_type", "1");
            query.put("login_sig", challenge.loginSig);
            query.put("pt_uistyle", "35");
            query.put("aid", Integer.toString(config.appId()));
            query.put("daid", Integer.toString(config.daid()));
            query.put("pt_3rd_aid", Integer.toString(config.thirdAppId()));
            String body = get(challenge.client, URI.create("https://ssl.ptlogin2.qq.com/ptqrlogin?" + params(query))).body();
            List<String> callback = parsePtuiCallback(body);
            if (callback.isEmpty()) {
                String preview = body.replaceAll("[\\r\\n]+", " ");
                if (preview.length() > 160) preview = preview.substring(0, 160);
                return new QrPollResult(QrPollResult.Status.WAITING, "QQ 返回了未识别的中间状态: " + preview, null);
            }
            String code = callback.get(0);
            String successUrl = callback.size() > 2 ? callback.get(2) : "";
            String message = callback.size() > 4 ? callback.get(4) : "QQ 状态 " + code;
            return switch (code) {
                case "66" -> new QrPollResult(QrPollResult.Status.WAITING, "等待扫码", null);
                case "67" -> new QrPollResult(QrPollResult.Status.SCANNED, "已扫码，请在手机确认", null);
                case "65" -> new QrPollResult(QrPollResult.Status.EXPIRED, "二维码已过期", null);
                case "0" -> complete(challengeId, challenge, successUrl);
                default -> new QrPollResult(QrPollResult.Status.FAILED, message, null);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new QrPollResult(QrPollResult.Status.FAILED, "QQ 轮询失败: " + e.getMessage(), null);
        } catch (Exception e) {
            return new QrPollResult(QrPollResult.Status.FAILED, "QQ 登录处理失败: " + e.getMessage(), null);
        }
    }

    private QrPollResult complete(String id, Challenge challenge, String successUrl) throws IOException, InterruptedException {
        String initial = cleanRedirectValue(successUrl);
        String token = tokenFrom(initial).orElse(null);
        String credentialSource = token == null ? "" : initial;
        URI current = token == null ? normalizeTencentCallback(browserUri(initial)) : null;
        for (int i = 0; i < 16 && token == null; i++) {
            if (token != null) break;
            if (!("http".equalsIgnoreCase(current.getScheme()) || "https".equalsIgnoreCase(current.getScheme())))
                break;
            HttpResponse<String> response = get(challenge.client, current);
            String responseBody = response.body() == null ? "" : response.body();
            String location = cleanRedirectValue(response.headers().firstValue("location")
                    .orElseGet(() -> redirectFromBody(responseBody).orElse("")));
            Optional<String> locationToken = tokenFrom(location);
            Optional<String> bodyToken = tokenFrom(responseBody);
            token = locationToken.or(() -> bodyToken).orElse(null);
            if (token != null)
                credentialSource = locationToken.isPresent() ? location : responseBody;
            if (token != null) break;
            if (location.isBlank()) {
                // check_sig can finish by setting cookies and returning a page without
                // a Location header. Continue with the m_authorize u1 from the APK.
                if (current.getHost().contains("ptlogin2.qq.com")) {
                    current = URI.create("http://connect.qq.com");
                    continue;
                }
                break;
            }
            current = normalizeTencentCallback(resolveBrowserUri(current, location));
        }
        if (token == null)
            return new QrPollResult(QrPollResult.Status.FAILED, "QQ 已确认，但 OAuth 回调缺少 access_token", null);
        String openIdValue = fragmentValue(credentialSource, "openid").orElse("");
        if (openIdValue.isBlank()) {
            String me = get(challenge.client, URI.create("https://graph.qq.com/oauth2.0/me?access_token=" + enc(token))).body();
            Matcher openId = OPEN_ID.matcher(me);
            if (!openId.find())
                return new QrPollResult(QrPollResult.Status.FAILED, "QQ OAuth 未返回 openid", null);
            openIdValue = openId.group(1);
        }
        String source = credentialSource;
        String pfKey = fragmentValue(source, "pfkey")
                .or(() -> fragmentValue(source, "pf_key"))
                .or(() -> fragmentValue(source, "key")).orElse("");
        String payToken = fragmentValue(source, "pay_token").orElse("");
        PlatformCredential credential;
        try {
            credential = msdk.exchange(openIdValue, token, payToken, pfKey);
        } catch (Exception failure) {
            return new QrPollResult(QrPollResult.Status.FAILED,
                    "QQ OpenSDK 已确认，但 MSDK 换票失败: " + failure.getMessage(), null);
        }
        challenges.remove(id);
        return new QrPollResult(QrPollResult.Status.CONFIRMED, "QQ 扫码登录成功", credential);
    }

    @Override
    public byte[] image(String challengeId) {
        return required(challengeId).png.clone();
    }

    static int qrToken(String value) {
        int result = 0;
        for (int i = 0; i < value.length(); i++) result += (result << 5) + value.charAt(i);
        return result & 0x7fffffff;
    }

    /**
     * OpenSDK SystemUtils.getAppSignatureMD5(package + cert MD5 + epoch seconds).
     */
    static String appSign(String packageName, String certificateMd5, String epochSeconds) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(
                    (packageName + '_' + certificateMd5.toLowerCase() + '_' + epochSeconds)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception failure) {
            throw new IllegalStateException("QQ 应用签名计算失败", failure);
        }
    }

    /**
     * Parses ptuiCB with 6, 7 or later argument layouts and JavaScript escapes.
     */
    static List<String> parsePtuiCallback(String body) {
        if (body == null) return List.of();
        int name = body.indexOf("ptuiCB");
        int open = name < 0 ? -1 : body.indexOf('(', name + 6);
        if (open < 0) return List.of();
        List<String> values = new ArrayList<>();
        int cursor = open + 1;
        while (cursor < body.length()) {
            while (cursor < body.length() && (Character.isWhitespace(body.charAt(cursor)) || body.charAt(cursor) == ','))
                cursor++;
            if (cursor >= body.length() || body.charAt(cursor) == ')') break;
            char quote = body.charAt(cursor);
            if (quote != '\'' && quote != '"') return List.of();
            cursor++;
            StringBuilder value = new StringBuilder();
            boolean closed = false;
            while (cursor < body.length()) {
                char next = body.charAt(cursor++);
                if (next == quote) {
                    closed = true;
                    break;
                }
                if (next != '\\') {
                    value.append(next);
                    continue;
                }
                if (cursor >= body.length()) break;
                char escaped = body.charAt(cursor++);
                if (escaped == 'x' && cursor + 2 <= body.length()) {
                    try {
                        value.append((char) Integer.parseInt(body.substring(cursor, cursor + 2), 16));
                        cursor += 2;
                    } catch (NumberFormatException ignored) {
                        value.append(escaped);
                    }
                } else if (escaped == 'u' && cursor + 4 <= body.length()) {
                    try {
                        value.append((char) Integer.parseInt(body.substring(cursor, cursor + 4), 16));
                        cursor += 4;
                    } catch (NumberFormatException ignored) {
                        value.append(escaped);
                    }
                } else value.append(switch (escaped) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> escaped;
                });
            }
            if (!closed) return List.of();
            values.add(value.toString());
        }
        return List.copyOf(values);
    }

    /**
     * QQ mobile returns a check_sig link on openmobile.qq.com, while the confirmed
     * session cookies are scoped to qq.com / ptlogin2.qq.com.  Following that host
     * literally drops the login cookies in a standards-compliant CookieManager and
     * Tencent replies 403.  The browser-compatible endpoint is the same path on
     * ssl.ptlogin2.qq.com, where both cookie scopes are valid.
     */
    static URI normalizeTencentCallback(URI value) {
        if (value == null || value.getHost() == null || value.getPath() == null
                || !value.getHost().toLowerCase().endsWith(".openmobile.qq.com")
                || !value.getPath().endsWith("/check_sig")) return value;
        String original = value.toASCIIString();
        int hostAt = original.indexOf(value.getRawAuthority());
        if (hostAt < 0) throw new IllegalArgumentException("QQ 回调地址无效");
        String authority = value.getRawAuthority().replace(value.getHost(), "ssl.ptlogin2.qq.com");
        String rewritten = original.substring(0, hostAt) + authority
                + original.substring(hostAt + value.getRawAuthority().length());
        if (rewritten.startsWith("http://"))
            rewritten = "https://" + rewritten.substring("http://".length());
        return URI.create(rewritten);
    }

    /**
     * Exact x.open.getFlexOpenData fallback used by QQ's current login_10.js.
     */
    static String openLoginData(URI xlogin) {
        if (xlogin == null || xlogin.getRawQuery() == null || xlogin.getRawQuery().isBlank()) {
            throw new IllegalArgumentException("QQ xlogin 缺少查询参数");
        }
        return xlogin.getRawQuery() + "&pt_flex=1";
    }

    static URI extractAuthorizeSource(String page) {
        Matcher matcher = AUTHORIZE_SRC.matcher(page == null ? "" : page);
        if (!matcher.find()) throw new IllegalStateException("QQ m_authorize 响应缺少 xlogin 地址");
        String decoded = decodeJavascriptString(matcher.group(1));
        URI uri = URI.create(decoded);
        if (!"xui.ptlogin2.qq.com".equalsIgnoreCase(uri.getHost()) || !uri.getPath().endsWith("/xlogin")) {
            throw new IllegalStateException("QQ m_authorize 返回了异常的 xlogin 地址");
        }
        return uri;
    }

    private static String decodeJavascriptString(String source) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value != '\\' || i + 1 >= source.length()) {
                result.append(value);
                continue;
            }
            char escaped = source.charAt(++i);
            if (escaped == 'x' && i + 2 < source.length()) {
                try {
                    result.append((char) Integer.parseInt(source.substring(i + 1, i + 3), 16));
                    i += 2;
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
            if (escaped == 'u' && i + 4 < source.length()) {
                try {
                    result.append((char) Integer.parseInt(source.substring(i + 1, i + 5), 16));
                    i += 4;
                    continue;
                } catch (NumberFormatException ignored) {
                }
            }
            result.append(switch (escaped) {
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                default -> escaped;
            });
        }
        return result.toString();
    }

    private Challenge required(String id) {
        Challenge value = challenges.get(id);
        if (value == null) throw new IllegalArgumentException("扫码会话不存在或已结束");
        return value;
    }

    private static HttpResponse<String> get(HttpClient client, URI uri) throws IOException, InterruptedException {
        return client.send(request(uri).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpResponse<byte[]> getBytes(HttpClient client, URI uri) throws IOException, InterruptedException {
        return client.send(request(uri).header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8").build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    private static HttpRequest.Builder request(URI uri) {
        return HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).header("User-Agent", UA)
                .header("Referer", "https://xui.ptlogin2.qq.com/");
    }

    private static Optional<String> cookie(CookieManager manager, String name) {
        return manager.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(name)).map(c -> c.getValue()).findFirst();
    }

    private static Optional<String> fragmentValue(String value, String name) {
        if (value == null) return Optional.empty();
        int mark = Math.max(value.indexOf('#'), value.indexOf('?'));
        if (mark < 0) return Optional.empty();
        for (String item : value.substring(mark + 1).split("&")) {
            String[] pair = item.split("=", 2);
            if (pair[0].equals(name) && pair.length == 2)
                return Optional.of(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return Optional.empty();
    }

    static Optional<String> callbackValue(String value, String name) {
        return fragmentValue(cleanRedirectValue(value), name);
    }

    static Optional<String> tokenFrom(String value) {
        String normalized = cleanRedirectValue(value);
        Optional<String> parsed = fragmentValue(normalized, "access_token");
        if (parsed.isPresent()) return parsed;
        Matcher matcher = ACCESS_TOKEN.matcher(normalized);
        return matcher.find() ? Optional.of(URLDecoder.decode(matcher.group(1), StandardCharsets.UTF_8)) : Optional.empty();
    }

    private static Optional<String> redirectFromBody(String body) {
        Matcher matcher = REDIRECT_URL.matcher(body == null ? "" : body);
        return matcher.find() ? Optional.of(matcher.group(1).replace("\\/", "/")) : Optional.empty();
    }

    private static String htmlDecode(String value) {
        return value == null ? "" : value.replace("&amp;", "&").replace("&#38;", "&");
    }

    /**
     * Accept browser-tolerated Tencent URLs, including Markdown wrappers and repeated '#'.
     */
    static URI browserUri(String value) {
        String normalized = cleanRedirectValue(value);
        int firstHash = normalized.indexOf('#');
        if (firstHash >= 0 && normalized.indexOf('#', firstHash + 1) >= 0) {
            normalized = normalized.substring(0, firstHash + 1)
                    + normalized.substring(firstHash + 1).replace("#", "%23");
        }
        return URI.create(normalized);
    }

    private static URI resolveBrowserUri(URI base, String value) {
        URI parsed = browserUri(value);
        return parsed.isAbsolute() ? parsed : base.resolve(parsed);
    }

    private static String cleanRedirectValue(String value) {
        String normalized = htmlDecode(value).trim()
                .replace("\\/", "/").replace("\\_", "_").replace("\\&", "&");
        if (normalized.startsWith("[") && normalized.contains("](http")) {
            int open = normalized.indexOf("](") + 2;
            int close = normalized.lastIndexOf(')');
            if (close > open) normalized = normalized.substring(open, close);
        }
        return normalized;
    }

    private static String params(Map<String, String> source) {
        return source.entrySet().stream().map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .reduce((a, b) -> a + "&" + b).orElse("");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record Challenge(HttpClient client, CookieManager cookies, byte[] png, String qrSig,
                             String loginSig,
                             String openLoginData, Instant expiresAt) {
    }
}
