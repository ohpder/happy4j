package cc.superbaby.happy4j.domain;

/**
 * Actions the server currently permits the local player to submit.
 */
public enum TableAction {
    READY,
    CALL_LANDLORD,
    PASS_LANDLORD,
    ROB_LANDLORD,
    PASS_ROB,
    DOUBLE,
    SUPER_DOUBLE,
    NO_DOUBLE,
    PLAY,
    PASS,
    TRUST,
    CANCEL_TRUST,
    CHANGE_TABLE,
    LEAVE_ROOM
}
