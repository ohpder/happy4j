package cc.superbaby.happy4j.infrastructure.auth.game;

import javax.crypto.Cipher;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class SafeLoginCrypto {
    private static final byte[] RSA_ALGORITHM = Base64.getDecoder().decode("MA0GCSqGSIb3DQEBAQUA");
    private final PublicKey key;
    private final int modulusBytes;

    SafeLoginCrypto(String pem) {
        try {
            byte[] pkcs1 = Base64.getMimeDecoder().decode(pem
                    .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                    .replace("-----END RSA PUBLIC KEY-----", ""));
            byte[] bitString = der(0x03, concat(new byte[]{0}, pkcs1));
            byte[] subjectPublicKeyInfo = der(0x30, concat(RSA_ALGORITHM, bitString));
            key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(subjectPublicKeyInfo));
            modulusBytes = (((java.security.interfaces.RSAPublicKey) key).getModulus().bitLength() + 7) / 8;
        } catch (Exception e) {
            throw new IllegalStateException("解析 TSDK RSA 公钥失败", e);
        }
    }

    String encrypt(String value) {
        try {
            byte[] plain = value.getBytes(StandardCharsets.UTF_8);
            if (plain.length > modulusBytes)
                throw new IllegalArgumentException("TSDK RSA 明文超过模长");
            // COpensslRSA::PublicEncrypt passes RSA_size bytes and
            // RSA_NO_PADDING (3): plaintext at the start, zero-filled tail.
            byte[] nativeBlock = new byte[modulusBytes];
            System.arraycopy(plain, 0, nativeBlock, 0, plain.length);
            Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return Base64.getEncoder().encodeToString(cipher.doFinal(nativeBlock));
        } catch (Exception e) {
            throw new IllegalStateException("TSDK SafeLogin RSA 加密失败", e);
        }
    }

    private static byte[] der(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        if (value.length < 128) out.write(value.length);
        else if (value.length < 256) {
            out.write(0x81);
            out.write(value.length);
        } else {
            out.write(0x82);
            out.write(value.length >>> 8);
            out.write(value.length);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] result = new byte[left.length + right.length];
        System.arraycopy(left, 0, result, 0, left.length);
        System.arraycopy(right, 0, result, left.length, right.length);
        return result;
    }
}
