package cc.superbaby.happy4j.application;

/**
 * Stable names for runtime configuration persisted in {@code server_config}.
 */
public final class ServerConfigKeys {
    public static final String MISSION_SEED_IDS = "mission.seed-ids";
    public static final String MISSION_SYNC_RANGE = "mission.sync-range";
    public static final String MISSION_CATALOG_TTL_SECONDS = "mission.catalog-ttl-seconds";
    public static final String SESSION_RESTORE_ENABLED = "session.restore-enabled";
    public static final String RUNTIME_MAX_ACTIVE_USERS = "runtime.max-active-users";
    public static final String RUNTIME_MAX_ACTIVE_SESSIONS_PER_USER = "runtime.max-active-sessions-per-user";
    public static final String RUNTIME_WORKER_THREADS = "runtime.worker-threads";
    public static final String RUNTIME_WORKER_QUEUE_CAPACITY = "runtime.worker-queue-capacity";
    public static final String RUNTIME_QR_POLL_THREADS = "runtime.qr-poll-threads";

    private ServerConfigKeys() {
    }
}
