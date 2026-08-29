package cc.superbaby.happy4j.protocol.game;

public enum GameMessage {
    LOGIN(15801), LOGOUT(15802), SIT_DOWN(15803), STAND_UP(15804), QUERY_REPLAY(15806),
    ROOM_EVENT(15809), READY(15810), HEARTBEAT(15812), PLAY(15813), CALL_PLAYER(15814), GAME_SO(15815);
    private final int id;

    GameMessage(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public boolean matches(int messageId) {
        return id == messageId;
    }
}
