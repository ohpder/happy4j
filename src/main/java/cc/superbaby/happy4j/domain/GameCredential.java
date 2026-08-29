package cc.superbaby.happy4j.domain;

import java.util.HexFormat;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record GameCredential(
        long uin,
        long playerId,
        PlatformCredential.Provider provider,
        byte[] randomKey,
        byte[] sessionTicket,
        String enKey,
        String openId,
        String openKey,
        String payToken,
        String pf,
        String pfKey,
        long beans,
        long diamonds,
        Map<String, Object> raw) {
    public GameCredential {
        randomKey = randomKey == null ? new byte[0] : randomKey.clone();
        sessionTicket = sessionTicket == null ? new byte[0] : sessionTicket.clone();
        raw = raw == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(raw));
    }

    @Override
    public byte[] randomKey() {
        return randomKey.clone();
    }

    @Override
    public byte[] sessionTicket() {
        return sessionTicket.clone();
    }

    public String randomKeyHex() {
        return HexFormat.of().formatHex(randomKey);
    }

    // QR-scan login authenticates through the PC/plattype-4 SafeLogin branch (RequestPCLogin), not
    // the SDK-embedded RequestQQLogin/accountType-2 branch — verified against a live device capture
    // where accounttype=4 is used consistently for every QQ call after SafeLogin. SocketLotusTransport's
    // platformCode() already maps accountType 4 to platform -4 for exactly this reason.
    public int accountType() {
        return provider == PlatformCredential.Provider.WECHAT ? 1 : 4;
    }
}
