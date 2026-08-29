package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.ClientSession;
import cc.superbaby.happy4j.domain.RemoteEvent;
import cc.superbaby.happy4j.infrastructure.persistence.entity.GameAccountEntity;
import cc.superbaby.happy4j.infrastructure.persistence.entity.GameEventEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameAccountRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameClientSessionRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameEventRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Component
public class GameEventPersistenceWriter {
    private final GameClientSessionRepository sessions;
    private final GameEventRepository events;
    private final GameAccountRepository accounts;
    private final ObjectMapper json;

    public GameEventPersistenceWriter(GameClientSessionRepository sessions, GameEventRepository events,
                                      GameAccountRepository accounts, ObjectMapper json) {
        this.sessions = sessions;
        this.events = events;
        this.accounts = accounts;
        this.json = json;
    }

    @Transactional
    public void write(ClientSession aggregate, RemoteEvent event) {
        var entity = sessions.findByIdAndUserId(aggregate.id(), aggregate.ownerId()).orElse(null);
        if (entity == null) return;
        ClientSession.Snapshot snapshot = aggregate.snapshot();
        String provider = aggregate.gameCredential() == null ? null : aggregate.gameCredential().provider().name();
        Long uin = snapshot.uin() == 0 ? null : snapshot.uin();
        entity.synchronize(snapshot.phase().name(), snapshot.detail(), provider, uin, stringify(snapshot));
        events.save(new GameEventEntity(entity, entity.getUser(), event.index(), event.kind(),
                truncate(event.message(), 1024), stringify(event.data()), event.occurredAt()));
        if (provider != null && uin != null) {
            GameAccountEntity account = accounts.findByProviderAndGameUin(provider, uin)
                    .orElseGet(() -> new GameAccountEntity(UUID.randomUUID(), entity.getUser(), provider, uin));
            account.synchronize(snapshot.nickname(), snapshot.avatarUrl(), snapshot.beans(),
                    snapshot.diamonds(), snapshot.goldCoins());
            accounts.save(account);
        }
    }

    private String stringify(Object value) {
        try {
            return value == null ? null : json.writeValueAsString(value);
        } catch (Exception failure) {
            return "{\"serializationError\":\"" + failure.getClass().getSimpleName() + "\"}";
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
