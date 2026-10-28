package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.Card;
import cc.superbaby.happy4j.domain.GameStage;
import cc.superbaby.happy4j.domain.TableAction;
import cc.superbaby.happy4j.domain.TableSnapshot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerAiDecisionServiceTest {
    private final ServerAiDecisionService service = new ServerAiDecisionService();

    @Test
    void selectsAValidatedMoveOnAnOpenTurn() {
        TableSnapshot table = table(List.of(Card.fromCode(3), Card.fromCode(4), Card.fromCode(5)),
                List.of(), 0, 0, -1);

        var decision = service.choosePlay(table);

        assertThat(decision.pass()).isFalse();
        assertThat(decision.cards()).containsExactly(3);
        assertThat(service.legalMoves(table)).contains(decision.cards());
    }

    @Test
    void passesWhenNoMoveCanBeatThePreviousPlay() {
        TableSnapshot table = table(List.of(Card.fromCode(3), Card.fromCode(4)),
                List.of(Card.fromCode(54)), 25, 15, 1);

        var decision = service.choosePlay(table);

        assertThat(decision.pass()).isTrue();
        assertThat(decision.cards()).isEmpty();
        assertThat(decision.candidates()).isEqualTo(1);
    }

    private static TableSnapshot table(List<Card> hand, List<Card> lastPlay, int lastPlayType,
                                       int lastPlayRank, int lastPlaySeat) {
        return new TableSnapshot(1260, 10, 20, 0, 0, 1, hand, lastPlay, lastPlayType,
                lastPlayRank, lastPlaySeat, List.of(), List.of(), List.of(), GameStage.PLAYING,
                List.of(TableAction.PLAY, TableAction.PASS), 1, false, "轮到你出牌");
    }
}
