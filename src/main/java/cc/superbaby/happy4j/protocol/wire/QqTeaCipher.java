package cc.superbaby.happy4j.protocol.wire;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;

/**
 * Tencent oi_symmetry_encrypt2 / QQ-TEA packet cipher.
 */
public final class QqTeaCipher {
    private static final int BLOCK_SIZE = 8;
    private static final int KEY_SIZE = 16;
    private static final int DELTA = 0x9e3779b9;
    private static final int ROUNDS = 16;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private QqTeaCipher() {
    }

    public static byte[] encrypt(byte[] plaintext, byte[] key) {
        return encrypt(plaintext, key, SECURE_RANDOM);
    }

    /**
     * Random is injectable to reproduce captures and deterministic test vectors.
     */
    public static byte[] encrypt(byte[] plaintext, byte[] key, Random random) {
        Objects.requireNonNull(plaintext, "plaintext");
        requireKey(key);
        Objects.requireNonNull(random, "random");

        int padding = (BLOCK_SIZE - ((plaintext.length + 10) % BLOCK_SIZE)) % BLOCK_SIZE;
        byte[] framed = new byte[plaintext.length + padding + 10];
        framed[0] = (byte) ((random.nextInt() & 0xf8) | padding);
        for (int i = 1; i < padding + 3; i++) {
            framed[i] = (byte) random.nextInt();
        }
        System.arraycopy(plaintext, 0, framed, padding + 3, plaintext.length);
        // The final seven bytes remain zero by construction.

        byte[] output = new byte[framed.length];
        byte[] previousCipher = new byte[BLOCK_SIZE];
        byte[] previousXorPlain = new byte[BLOCK_SIZE];
        for (int offset = 0; offset < framed.length; offset += BLOCK_SIZE) {
            byte[] xorPlain = Arrays.copyOfRange(framed, offset, offset + BLOCK_SIZE);
            xorInPlace(xorPlain, previousCipher);
            byte[] encrypted = encryptBlock(xorPlain, key);
            xorInPlace(encrypted, previousXorPlain);
            System.arraycopy(encrypted, 0, output, offset, BLOCK_SIZE);
            previousXorPlain = xorPlain;
            previousCipher = encrypted;
        }
        return output;
    }

    public static byte[] decrypt(byte[] ciphertext, byte[] key) {
        Objects.requireNonNull(ciphertext, "ciphertext");
        requireKey(key);
        if (ciphertext.length < 16 || ciphertext.length % BLOCK_SIZE != 0) {
            throw new ProtocolException("QQ-TEA ciphertext length must be a multiple of 8 and at least 16");
        }

        byte[] framed = new byte[ciphertext.length];
        byte[] previousCipher = new byte[BLOCK_SIZE];
        byte[] previousXorPlain = new byte[BLOCK_SIZE];
        for (int offset = 0; offset < ciphertext.length; offset += BLOCK_SIZE) {
            byte[] cipherBlock = Arrays.copyOfRange(ciphertext, offset, offset + BLOCK_SIZE);
            byte[] teaInput = cipherBlock.clone();
            xorInPlace(teaInput, previousXorPlain);
            byte[] xorPlain = decryptBlock(teaInput, key);
            byte[] plainBlock = xorPlain.clone();
            xorInPlace(plainBlock, previousCipher);
            System.arraycopy(plainBlock, 0, framed, offset, BLOCK_SIZE);
            previousXorPlain = xorPlain;
            previousCipher = cipherBlock;
        }

        int padding = framed[0] & 0x07;
        int payloadStart = padding + 3;
        int payloadEnd = framed.length - 7;
        if (payloadStart > payloadEnd) {
            throw new ProtocolException("invalid QQ-TEA padding");
        }
        for (int i = payloadEnd; i < framed.length; i++) {
            if (framed[i] != 0) {
                throw new ProtocolException("invalid QQ-TEA zero trailer");
            }
        }
        return Arrays.copyOfRange(framed, payloadStart, payloadEnd);
    }

    static byte[] encryptBlock(byte[] input, byte[] key) {
        int y = readInt(input, 0);
        int z = readInt(input, 4);
        int a = readInt(key, 0);
        int b = readInt(key, 4);
        int c = readInt(key, 8);
        int d = readInt(key, 12);
        int sum = 0;
        for (int i = 0; i < ROUNDS; i++) {
            sum += DELTA;
            y += ((z << 4) + a) ^ (z + sum) ^ ((z >>> 5) + b);
            z += ((y << 4) + c) ^ (y + sum) ^ ((y >>> 5) + d);
        }
        byte[] output = new byte[BLOCK_SIZE];
        writeInt(output, 0, y);
        writeInt(output, 4, z);
        return output;
    }

    static byte[] decryptBlock(byte[] input, byte[] key) {
        int y = readInt(input, 0);
        int z = readInt(input, 4);
        int a = readInt(key, 0);
        int b = readInt(key, 4);
        int c = readInt(key, 8);
        int d = readInt(key, 12);
        int sum = DELTA * ROUNDS;
        for (int i = 0; i < ROUNDS; i++) {
            z -= ((y << 4) + c) ^ (y + sum) ^ ((y >>> 5) + d);
            y -= ((z << 4) + a) ^ (z + sum) ^ ((z >>> 5) + b);
            sum -= DELTA;
        }
        byte[] output = new byte[BLOCK_SIZE];
        writeInt(output, 0, y);
        writeInt(output, 4, z);
        return output;
    }

    private static int readInt(byte[] input, int offset) {
        return ((input[offset] & 0xff) << 24)
                | ((input[offset + 1] & 0xff) << 16)
                | ((input[offset + 2] & 0xff) << 8)
                | (input[offset + 3] & 0xff);
    }

    private static void writeInt(byte[] output, int offset, int value) {
        output[offset] = (byte) (value >>> 24);
        output[offset + 1] = (byte) (value >>> 16);
        output[offset + 2] = (byte) (value >>> 8);
        output[offset + 3] = (byte) value;
    }

    private static void xorInPlace(byte[] target, byte[] value) {
        for (int i = 0; i < BLOCK_SIZE; i++) {
            target[i] ^= value[i];
        }
    }

    private static void requireKey(byte[] key) {
        Objects.requireNonNull(key, "key");
        if (key.length != KEY_SIZE) {
            throw new IllegalArgumentException("QQ-TEA key must be exactly 16 bytes");
        }
    }
}
