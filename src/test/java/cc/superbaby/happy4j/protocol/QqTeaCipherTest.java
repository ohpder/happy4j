package cc.superbaby.happy4j.protocol.wire;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QqTeaCipherTest {
    private static final byte[] KEY = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    @Test
    void oiSymmetryRoundTripAndLengthRule() {
        for (byte[] input : new byte[][]{
                new byte[0],
                {1},
                "hlddz offline protocol".getBytes(StandardCharsets.UTF_8)}) {
            byte[] encrypted = QqTeaCipher.encrypt(input, KEY, new Random(363));
            assertEquals(0, encrypted.length % 8);
            assertArrayEquals(input, QqTeaCipher.decrypt(encrypted, KEY));
        }
    }

    @Test
    void deterministicOiSymmetryVector() {
        byte[] encrypted = QqTeaCipher.encrypt(
                "hlddz offline protocol".getBytes(StandardCharsets.UTF_8), KEY, new Random(363));
        assertEquals("bc0273b225d898590955199b970c3b3ca700d8ba0276411048dbff2655bc335f",
                HexFormat.of().formatHex(encrypted));
    }
}
