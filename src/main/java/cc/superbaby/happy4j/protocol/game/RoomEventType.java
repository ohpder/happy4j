package cc.superbaby.happy4j.protocol.game;

import java.util.Arrays;

public enum RoomEventType {
    EXIT_ROOM(2, "退出房间"), ENTER_TABLE(3, "进入房间"), LEAVE_TABLE(4, "离开牌桌"),
    GAME_START(5, "牌局开始"), READY(6, "准备"), STATE_UPDATE(7, "状态更新"),
    WATCH(8, "进入旁观"), BALANCE_CHANGED(11, "余额变化"), USER_KICKED(12, "账号在其他设备登录"),
    PLAYER_RESULT(13, "结算状态"), ITEM_CHANGED(15, "道具变化"), CANCEL_WATCH(17, "取消旁观"),
    UNKNOWN(-1, "未知房间事件");

    private final int code;
    private final String label;

    RoomEventType(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int code() {
        return code;
    }

    public String label() {
        return label;
    }

    public static RoomEventType fromCode(int code) {
        return Arrays.stream(values()).filter(value -> value.code == code).findFirst().orElse(UNKNOWN);
    }
}
