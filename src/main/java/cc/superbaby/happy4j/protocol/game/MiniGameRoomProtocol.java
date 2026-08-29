package cc.superbaby.happy4j.protocol.game;

import java.util.List;
import java.util.Map;

/**
 * Request profiles recovered from the WeChat mini-game's GameBase bundle.
 */
public final class MiniGameRoomProtocol {
    private MiniGameRoomProtocol() {
    }

    /**
     * GameBase ordinary matching: qAa(3, roomId, -1, -1, []).
     */
    public static Map<String, Object> quickMatchSitDown(int roomId) {
        return Map.of("SitDownFlag", 3, "RoomID", roomId, "TableID", -1, "SeatID", -1,
                "TLVNum", 0, "TLVInfo", List.of());
    }
}
