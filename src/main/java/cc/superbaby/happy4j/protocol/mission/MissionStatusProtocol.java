package cc.superbaby.happy4j.protocol.mission;

import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolRequest;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.protocol.wire.FunctionalEntity;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class MissionStatusProtocol {
    public static final int MESSAGE_ID = 7304;

    private final ObjectMapper json;
    private final Happy4jProperties.Game game;

    public MissionStatusProtocol(ObjectMapper json, Happy4jProperties properties) {
        this.json = json;
        this.game = properties.game();
    }

    /**
     * The JS client passes the activity-center mission IDs explicitly. MissionSvr does not expose
     * an enumerable catalog endpoint, so an empty list/range request is intentionally avoided.
     */
    public ProtocolRequest fetchKnown(GameCredential credential, List<Integer> missionIds) {
        try {
            List<Integer> ids = missionIds.stream().distinct().limit(400).toList();
            Map<String, Object> body = Map.ofEntries(
                    Map.entry("uin", credential.uin()),
                    Map.entry("transparent", 0),
                    Map.entry("gameId", game.id()),
                    Map.entry("sceneId", 0),
                    Map.entry("missionClientType", game.clientType()),
                    Map.entry("flag", 0),
                    Map.entry("beginMissionId", 0),
                    Map.entry("endMissionId", 0),
                    Map.entry("extFlag", 0),
                    Map.entry("missionIdNum", ids.size()),
                    Map.entry("missionId", ids),
                    Map.entry("groupId", 0));
            return new ProtocolRequest(MESSAGE_ID, FunctionalEntity.LOGIC_SERVICE_SERVER, (short) -1,
                    "Tdrpb.MissionSvrMsgPara.CReqFetchMissionStatus",
                    "Tdrpb.MissionSvrMsgPara.CRspFetchMissionStatus",
                    json.writeValueAsString(body), true);
        } catch (Exception failure) {
            throw new IllegalStateException("任务范围请求编码失败", failure);
        }
    }
}
