package cc.superbaby.happy4j.domain;

public enum ClientPhase {
    CREATED,
    QR_READY,
    QR_SCANNED,
    PLATFORM_AUTHENTICATED,
    GAME_AUTHENTICATING,
    HALL_CONNECTED,
    ENTERING_ROOM,
    AT_TABLE,
    READY,
    PLAYING,
    EVICTED,
    DISCONNECTED,
    FAILED
}
