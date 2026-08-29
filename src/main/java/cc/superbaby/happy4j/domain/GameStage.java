package cc.superbaby.happy4j.domain;

/**
 * Server-driven stages recovered from GameBase's GameSo subtype dispatcher.
 */
public enum GameStage {
    IDLE,
    LOCATING,
    SEATED,
    READY,
    RECOVERING,
    DEALING,
    CALLING_LANDLORD,
    DOUBLING,
    PLAYING,
    TIMED_OUT,
    SETTLED
}
