package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.ClientSession;
import cc.superbaby.happy4j.infrastructure.persistence.entity.GameClientSessionEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.AppUserRepository;
import cc.superbaby.happy4j.infrastructure.persistence.repository.GameClientSessionRepository;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Service
public class GameSessionPersistenceService {
    private final AppUserRepository users;
    private final GameClientSessionRepository sessions;
    private final GameEventPersistenceWriter writer;
    private final TaskExecutor executor;
    private final ObjectMapper json;

    public GameSessionPersistenceService(AppUserRepository users, GameClientSessionRepository sessions,
                                         GameEventPersistenceWriter writer,
                                         @Qualifier("persistenceExecutor") TaskExecutor executor,
                                         ObjectMapper json) {
        this.users = users;
        this.sessions = sessions;
        this.writer = writer;
        this.executor = executor;
        this.json = json;
    }

    @Transactional
    public void attach(ClientSession aggregate) {
        var user = users.findById(aggregate.ownerId()).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        var snapshot = aggregate.snapshot();
        sessions.save(new GameClientSessionEntity(aggregate.id(), user, snapshot.phase().name(),
                snapshot.detail(), stringify(snapshot)));
        aggregate.subscribe(event -> executor.execute(() -> writer.write(aggregate, event)));
    }

    public boolean existsForUser(UUID sessionId, UUID userId) {
        return sessions.findByIdAndUserId(sessionId, userId).isPresent();
    }

    private String stringify(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception failure) {
            throw new IllegalStateException("会话序列化失败", failure);
        }
    }
}
