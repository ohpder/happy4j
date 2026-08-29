package cc.superbaby.happy4j.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "happy4j")
public record Happy4jProperties(Auth auth, Game game, Security security) {
    public Happy4jProperties {
        if (security == null) security = new Security(Duration.ofDays(30), 8,
                "aGFwcHk0ai1kZXYtY3JlZGVudGlhbC1rZXktMzIhISE=", Duration.ofDays(7), java.util.List.of("admin"));
    }

    public record Security(Duration tokenTtl, int maxSessionsPerUser, String credentialKey,
                           Duration gameLoginTtl, java.util.List<String> adminUsers) {
        public Security {
            if (adminUsers == null) adminUsers = java.util.List.of("admin");
        }
    }

    public record Auth(Qq qq, Wechat wechat) {
        public record Qq(
                int appId,
                int thirdAppId,
                int daid,
                String packageName,
                String signatureMd5,
                String statusOs,
                int statusVersion,
                String statusMachine,
                int msdkChannelId) {
        }

        public record Wechat(
                String appId,
                String scope,
                String sdkTicket,
                String appSecret,
                String msdkBaseUrl,
                int msdkGameId,
                int msdkChannelId,
                int msdkOs,
                String msdkVersion,
                String msdkSdkKey) {
        }
    }

    public record Game(
            int id,
            int clientType,
            int accountType,
            int clientVersion,
            int channelId,
            String osVersion,
            String safeLoginProtocol,
            String loginBaseUrl,
            String wechatLoginBaseUrl,
            String lotusHost,
            int lotusPort,
            Duration connectTimeout,
            Duration requestTimeout) {
    }
}
