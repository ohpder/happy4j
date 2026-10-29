package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.Card;
import cc.superbaby.happy4j.domain.GameStage;
import cc.superbaby.happy4j.domain.TableAction;
import cc.superbaby.happy4j.domain.TableSnapshot;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ServerOnnxDecisionServiceTest {
    @Test
    void usesTheBundledFullAutoModelForACompletePlayingSnapshot() {
        TableSnapshot table = new TableSnapshot(
                1, 100, 200, 0, 0, 0,
                List.of(1, 2, 3, 4, 14, 15, 16, 17, 18, 19,
                                27, 28, 29, 30, 40, 41, 42, 53, 54, 10).stream()
                        .map(Card::fromCode).toList(),
                List.of(), 0, 0, -1, List.of(),
                List.of(
                        new TableSnapshot.Player(1, "me", 0, 20, 0, false),
                        new TableSnapshot.Player(2, "up", 1, 17, 0, false),
                        new TableSnapshot.Player(3, "down", 2, 17, 0, false)),
                List.of(), GameStage.PLAYING, List.of(TableAction.PLAY, TableAction.PASS),
                1, false, "test");

        try (ServerOnnxDecisionService service = new ServerOnnxDecisionService(new ServerAiDecisionService(), "path/to/model")) {
            ServerAiDecisionService.PlayDecision decision = service.choosePlay(
                    table, new ServerAiDecisionService().legalMoves(table));
            assertThat(decision.reason()).startsWith("DouZero FullAuto ResNet ONNX");
            assertThat(decision.cards()).isNotNull();
        }
    }
}
