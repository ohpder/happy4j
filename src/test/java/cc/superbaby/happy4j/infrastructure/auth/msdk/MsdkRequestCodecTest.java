package cc.superbaby.happy4j.infrastructure.auth.msdk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MsdkRequestCodecTest {
    @Test
    void createsNativeFourNumericPartSequence() {
        assertThat(MsdkRequestCodec.nextSequence(1787795598L))
                .matches("1787795598-[0-9]{10}-[0-9]{6}-[0-9]{10}");
    }
}
