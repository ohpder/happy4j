package cc.superbaby.happy4j.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerProfileTest {
    @Test
    void appendsWechatAvatarSizeWhenProfileReturnsBareMmheadPath() {
        PlayerProfile profile = new PlayerProfile("玩家",
                "https://wx.qlogo.cn/mmhead/example-token", 0, Instant.now());

        assertThat(profile.avatarUrl()).isEqualTo("https://wx.qlogo.cn/mmhead/example-token/132");
        assertThat(PlayerProfile.normalizeAvatarUrl("http://q.qlogo.cn/example"))
                .isEqualTo("https://q.qlogo.cn/example");
    }
}
