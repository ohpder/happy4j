package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.protocol.game.MiniGameGameSoCodec.PlayerDetail;
import cc.superbaby.happy4j.domain.TableSnapshot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

class HappyDdzClientServiceStateTest {
    @Test
    void staleQuickGameLockAfterStandUpReturnsToSitDownFlow() {
        PlayerDetail standing = new PlayerDetail(42, 397911882L, 2, 88, 0, "我");
        PlayerDetail seated = new PlayerDetail(42, 397911882L, 4, 88, 0, "我");

        assertThat(HappyDdzClientService.lockedRouteNeedsRecovery(true, standing)).isFalse();
        assertThat(HappyDdzClientService.lockedRouteNeedsRecovery(true, seated)).isTrue();
        assertThat(HappyDdzClientService.lockedRouteNeedsRecovery(true, null)).isTrue();
        assertThat(HappyDdzClientService.lockedRouteNeedsRecovery(false, seated)).isFalse();
    }

    @Test
    void superDoubleRequiresAConsumableCard() {
        assertThatThrownBy(() -> HappyDdzClientService.requireSuperDoubleCard(0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("超级加倍卡数量不足");
        HappyDdzClientService.requireSuperDoubleCard(1);
    }

    @Test
    void mergesAllSeatProfilesAndPreservesCompleteFieldsAcrossPartialEvents() {
        List<TableSnapshot.Player> existing = List.of(
                new TableSnapshot.Player(10, "夏日橘子", 0, 17, 8800, false,
                        "https://img.example/a.png"));
        List<PlayerDetail> details = List.of(
                new PlayerDetail(42, 10, 4, 88, 0, "", "", 0),
                new PlayerDetail(43, 11, 5, 88, 1, "一只小黄鸡",
                        "https://img.example/b.png", 9900));

        List<TableSnapshot.Player> players = HappyDdzClientService.mergePlayerDetails(existing, details);

        assertThat(players).hasSize(2);
        assertThat(players.get(0).nickname()).isEqualTo("夏日橘子");
        assertThat(players.get(0).avatarUrl()).isEqualTo("https://img.example/a.png");
        assertThat(players.get(0).ready()).isFalse();
        assertThat(players.get(1).nickname()).isEqualTo("一只小黄鸡");
        assertThat(players.get(1).avatarUrl()).isEqualTo("https://img.example/b.png");
        assertThat(players.get(1).ready()).isTrue();
    }
}
