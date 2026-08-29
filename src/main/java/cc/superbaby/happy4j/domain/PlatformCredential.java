package cc.superbaby.happy4j.domain;

public record PlatformCredential(
        Provider provider,
        String openId,
        String accessToken,
        String pf,
        String pfKey,
        String payToken) {
    public enum Provider {QQ, WECHAT}
}
