package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.PlatformCredential;
import cc.superbaby.happy4j.infrastructure.persistence.entity.GameLoginStateEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.AppUserRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameLoginStateRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class CredentialVaultService {
    private static final String ACTIVE = "ACTIVE";

    private final GameLoginStateRepository states;
    private final AppUserRepository users;
    private final ObjectMapper json;
    private final byte[] key;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public CredentialVaultService(GameLoginStateRepository states,
                                  AppUserRepository users,
                                  ObjectMapper json,
                                  Happy4jProperties properties) {
        this.states = states;
        this.users = users;
        this.json = json;
        this.ttl = properties.security().gameLoginTtl();
        this.key = Base64.getDecoder().decode(properties.security().credentialKey());
        if (key.length != 32) {
            throw new IllegalStateException("HAPPY4J_CREDENTIAL_KEY 必须是 Base64 编码的 32 字节密钥");
        }
    }

    @Transactional
    public void store(UUID userId, GameCredential credential) {
        states.findByUserIdAndStatus(userId, ACTIVE).forEach(GameLoginStateEntity::invalidate);
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        byte[] encrypted = crypt(Cipher.ENCRYPT_MODE, iv, userId, serialize(credential));
        states.save(new GameLoginStateEntity(
                UUID.randomUUID(),
                users.findById(userId).orElseThrow(),
                credential.provider().name(),
                credential.uin(),
                encrypted,
                iv,
                Instant.now().plus(ttl)));
    }

    @Transactional
    public Optional<GameCredential> restore(UUID userId) {
        return states.findFirstByUserIdAndStatusAndExpiresAtAfterOrderByLastUsedAtDesc(
                        userId, ACTIVE, Instant.now())
                .map(entity -> {
                    try {
                        byte[] plaintext = crypt(Cipher.DECRYPT_MODE, entity.getIv(), userId,
                                entity.getEncryptedPayload());
                        CredentialPayload payload = json.readValue(plaintext, CredentialPayload.class);
                        entity.used();
                        return payload.credential();
                    } catch (Exception failure) {
                        entity.invalidate();
                        throw new IllegalStateException("已保存的游戏登录状态解密失败", failure);
                    }
                });
    }

    @Transactional
    public void invalidate(UUID userId) {
        states.findByUserIdAndStatus(userId, ACTIVE).forEach(GameLoginStateEntity::invalidate);
    }

    private byte[] serialize(GameCredential value) {
        try {
            return json.writeValueAsBytes(CredentialPayload.of(value));
        } catch (Exception failure) {
            throw new IllegalStateException("游戏登录状态序列化失败", failure);
        }
    }

    private byte[] crypt(int mode, byte[] iv, UUID userId, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(ByteBuffer.allocate(16)
                    .putLong(userId.getMostSignificantBits())
                    .putLong(userId.getLeastSignificantBits())
                    .array());
            return cipher.doFinal(input);
        } catch (Exception failure) {
            throw new IllegalStateException("游戏登录状态加解密失败", failure);
        }
    }

    private record CredentialPayload(long uin,
                                     long playerId,
                                     PlatformCredential.Provider provider,
                                     byte[] randomKey,
                                     byte[] sessionTicket,
                                     String enKey,
                                     String openId,
                                     String openKey,
                                     String payToken,
                                     String pf,
                                     String pfKey,
                                     long beans,
                                     long diamonds,
                                     Map<String, Object> raw) {
        static CredentialPayload of(GameCredential credential) {
            return new CredentialPayload(
                    credential.uin(), credential.playerId(), credential.provider(), credential.randomKey(),
                    credential.sessionTicket(), credential.enKey(), credential.openId(), credential.openKey(),
                    credential.payToken(), credential.pf(), credential.pfKey(), credential.beans(),
                    credential.diamonds(), credential.raw());
        }

        GameCredential credential() {
            return new GameCredential(uin, playerId, provider, randomKey, sessionTicket, enKey,
                    openId, openKey, payToken, pf, pfKey, beans, diamonds, raw);
        }
    }
}
