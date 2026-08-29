package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.config.Happy4jProperties;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

@Service
public class SecretCipherService {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipherService(Happy4jProperties properties) {
        key = Base64.getDecoder().decode(properties.security().credentialKey());
        if (key.length != 32)
            throw new IllegalStateException("HAPPY4J_CREDENTIAL_KEY 必须是 Base64 编码的 32 字节密钥");
    }

    public EncryptedSecret encrypt(UUID ownerId, String purpose, String plaintext) {
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        return new EncryptedSecret(crypt(Cipher.ENCRYPT_MODE, ownerId, purpose, iv,
                plaintext.getBytes(StandardCharsets.UTF_8)), iv);
    }

    public String decrypt(UUID ownerId, String purpose, byte[] encrypted, byte[] iv) {
        return new String(crypt(Cipher.DECRYPT_MODE, ownerId, purpose, iv, encrypted),
                StandardCharsets.UTF_8);
    }

    private byte[] crypt(int mode, UUID ownerId, String purpose, byte[] iv, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] purposeBytes = purpose.getBytes(StandardCharsets.UTF_8);
            cipher.updateAAD(ByteBuffer.allocate(20 + purposeBytes.length)
                    .putLong(ownerId.getMostSignificantBits())
                    .putLong(ownerId.getLeastSignificantBits())
                    .putInt(purposeBytes.length)
                    .put(purposeBytes)
                    .array());
            return cipher.doFinal(input);
        } catch (Exception failure) {
            throw new IllegalStateException("敏感配置加解密失败", failure);
        }
    }

    public record EncryptedSecret(byte[] encrypted, byte[] iv) {
        public EncryptedSecret {
            encrypted = encrypted.clone();
            iv = iv.clone();
        }
    }
}
