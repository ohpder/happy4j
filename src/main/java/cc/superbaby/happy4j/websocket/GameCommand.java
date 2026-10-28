package cc.superbaby.happy4j.websocket;

/**
 * Stable WebSocket application commands; browser code never sends protocol numbers.
 */
public enum GameCommand {
    BOOT,
    SESSION_CREATE,
    SESSION_RESUME,
    SESSION_CLOSE,
    AI_CONFIG_GET,
    AI_CONFIG_SAVE,
    AI_STOP,
    AI_PROVIDER_TEST,
    AI_DECIDE,
    SNAPSHOT_GET,
    QR_START,
    WALLET_REFRESH,
    PROFILE_REFRESH,
    MISSIONS_REFRESH,
    MISSION_CLAIM,
    ACTIVITIES_REFRESH,
    SIGN_IN_CLAIM,
    TREASURE_OPEN,
    MODE_ENTER,
    READY,
    ORDER,
    MULTIPLE,
    SUPER_MULTIPLE,
    TRUST,
    PLAY,
    CHANGE_TABLE,
    LEAVE_ROOM
}
