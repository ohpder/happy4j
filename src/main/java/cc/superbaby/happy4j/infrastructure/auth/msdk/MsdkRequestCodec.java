package cc.superbaby.happy4j.infrastructure.auth.msdk;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Canonical MSDK FaaS query/signature and request sequence generator.
 */
public final class MsdkRequestCodec {
    private static final SecureRandom RANDOM = new SecureRandom();

    private MsdkRequestCodec() {
    }

    public static String nextSequence(long epochSeconds) {
        return String.format("%010d-%010d-%06d-%010d", epochSeconds,
                1_000_000_000L + RANDOM.nextLong(9_000_000_000L),
                100_000 + RANDOM.nextInt(900_000),
                1_000_000_000L + RANDOM.nextLong(9_000_000_000L));
    }

    public static String canonical(int channelId, int gameId, int os, String sequence,
                                   long timestamp, String lang, String version) {
        return "channelid=" + channelId + "&from=msdkpix&gameid=" + gameId
                + "&lang=" + enc(lang) + "&os=" + os + "&seq=" + enc(sequence)
                + "&ts=" + timestamp + "&version=" + enc(version);
    }

    public static String signature(String path, String canonical, String sdkKey, String body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                    .digest((path + '?' + canonical + sdkKey + body).getBytes(StandardCharsets.UTF_8))) + '1';
        } catch (Exception failure) {
            throw new IllegalStateException("MSDK 请求签名失败", failure);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
