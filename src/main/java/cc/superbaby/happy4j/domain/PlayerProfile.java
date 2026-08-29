package cc.superbaby.happy4j.domain;

import java.time.Instant;

/**
 * Hall identity returned by the same CGI 1028/1029 path used by the wxpkg.
 */
public record PlayerProfile(String nickname, String avatarUrl, int gender, Instant refreshedAt) {
    public PlayerProfile {
        nickname = nickname == null || nickname.isBlank() ? "欢乐玩家" : nickname;
        avatarUrl = normalizeAvatarUrl(avatarUrl);
        refreshedAt = refreshedAt == null ? Instant.EPOCH : refreshedAt;
    }

    public static String normalizeAvatarUrl(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.trim().replaceFirst("^http:", "https:");
        if (normalized.startsWith("https://wx.qlogo.cn/mmhead/")
                && !normalized.matches(".*/(?:0|46|64|96|132)$")) {
            normalized += "/132";
        }
        return normalized;
    }

    public static PlayerProfile empty() {
        return new PlayerProfile("欢乐玩家", "", 0, Instant.EPOCH);
    }
}
