package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.Card;
import cc.superbaby.happy4j.domain.GameStage;
import cc.superbaby.happy4j.domain.TableAction;
import cc.superbaby.happy4j.domain.TableSnapshot;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PerfectDouDecisionServiceTest {
    @Test
    void loadsThePublishedPerfectDouOnnxModelAndReturnsALegalMove() {
        TableSnapshot table = new TableSnapshot(1, 10, 20, 0, 0, 0,
                List.of(Card.fromCode(3), Card.fromCode(16), Card.fromCode(5)), List.of(), 0, 0, -1,
                List.of(), List.of(
                        new TableSnapshot.Player(1, "me", 0, 3, 0, false),
                        new TableSnapshot.Player(2, "up", 1, 17, 0, false),
                        new TableSnapshot.Player(3, "down", 2, 17, 0, false)), List.of(),
                GameStage.PLAYING, List.of(TableAction.PLAY, TableAction.PASS), 1, false, "test");
        try (PerfectDouDecisionService service = new PerfectDouDecisionService(new JsonMapper(),
                "frontend/public/models/perfectdou/")) {
            ServerAiDecisionService.PlayDecision decision = service.choosePlay(table,
                    List.of(List.of(3, 16), List.of(5), List.of()));
            assertThat(List.of(List.of(3, 16), List.of(5), List.of())).contains(decision.cards());
            assertThat(decision.candidates()).isEqualTo(3);
        }
    }
}
