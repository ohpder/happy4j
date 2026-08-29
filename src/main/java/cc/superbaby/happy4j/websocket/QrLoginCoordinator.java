package cc.superbaby.happy4j.websocket;

import cc.superbaby.happy4j.application.HappyDdzClientService;
import cc.superbaby.happy4j.application.ServerConfigKeys;
import cc.superbaby.happy4j.application.ServerConfigService;
import cc.superbaby.happy4j.application.port.QrAuthPort;
import cc.superbaby.happy4j.domain.ClientSession;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Component
public class QrLoginCoordinator implements AutoCloseable {
    private final HappyDdzClientService clients;
    private final UserWebSocketHub hub;
    private final java.util.concurrent.ScheduledExecutorService scheduler;
    private final Map<UUID, ScheduledFuture<?>> polls = new ConcurrentHashMap<>();

    public QrLoginCoordinator(HappyDdzClientService clients, UserWebSocketHub hub,
                              ServerConfigService serverConfig) {
        this.clients = clients;
        this.hub = hub;
        int threads = Math.max(2, Math.min(32,
                serverConfig.integer(ServerConfigKeys.RUNTIME_QR_POLL_THREADS, 8)));
        this.scheduler = Executors.newScheduledThreadPool(threads, runnable -> {
            Thread thread = new Thread(runnable, "qr-login-poller");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void start(ClientSession session) {
        ScheduledFuture<?> old = polls.remove(session.id());
        if (old != null) old.cancel(false);
        polls.put(session.id(), scheduler.scheduleWithFixedDelay(() -> poll(session), 300, 1100, TimeUnit.MILLISECONDS));
    }

    private void poll(ClientSession session) {
        try {
            QrAuthPort.QrPollResult result = clients.pollQr(session.id());
            hub.send(session.ownerId(), WebSocketEnvelope.of("QR_STATUS", null,
                    Map.of("status", result.status(), "message", result.message(), "sessionId", session.id())));
            if (result.status() == QrAuthPort.QrPollResult.Status.CONFIRMED
                    || result.status() == QrAuthPort.QrPollResult.Status.EXPIRED
                    || result.status() == QrAuthPort.QrPollResult.Status.FAILED) stop(session.id());
        } catch (RuntimeException failure) {
            hub.send(session.ownerId(), WebSocketEnvelope.of("ERROR", null,
                    Map.of("message", failure.getMessage(), "sessionId", session.id())));
            stop(session.id());
        }
    }

    public void stop(UUID sessionId) {
        ScheduledFuture<?> task = polls.remove(sessionId);
        if (task != null) task.cancel(false);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
