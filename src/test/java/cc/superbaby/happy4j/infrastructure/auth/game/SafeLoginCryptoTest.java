package cc.superbaby.happy4j.infrastructure.auth.game;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

class SafeLoginCryptoTest {
    @Test
    void encryptsBuildSafeLoginParamWithApkPublicKey() throws Exception {
        String pem = new ClassPathResource("hlddz/pubkey.key").getContentAsString(StandardCharsets.US_ASCII);
        String encrypted = new SafeLoginCrypto(pem).encrypt("a1=open&a2=token&a3=0123456789abcdef");
        byte[] nativeCiphertext = Base64.getDecoder().decode(encrypted);
        assertThat(nativeCiphertext).hasSize(256);
        assertThat(java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(nativeCiphertext)))
                .isEqualTo("6ff3d3c6bc661eb67b55e9d3694b81865b4ab2e473b8af2a7364553ef320b999");
        assertThat(encrypted).doesNotContain("open", "token");
    }
}
