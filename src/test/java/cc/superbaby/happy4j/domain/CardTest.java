package cc.superbaby.happy4j.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardTest {
    @Test
    void decodesWxpkgSequentialDeckCodes() {
        assertThat(Card.fromCode(1)).extracting(Card::rank, Card::suit).containsExactly("A", "♠");
        assertThat(Card.fromCode(13)).extracting(Card::rank, Card::suit).containsExactly("K", "♠");
        assertThat(Card.fromCode(14)).extracting(Card::rank, Card::suit).containsExactly("A", "♥");
        assertThat(Card.fromCode(53).rank()).isEqualTo("小王");
        assertThat(Card.fromCode(54).rank()).isEqualTo("大王");
    }
}
