package cc.superbaby.happy4j.protocol;

import cc.superbaby.happy4j.application.port.MissionCatalogPort;
import cc.superbaby.happy4j.protocol.cgi.HallCgiProtocol;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HallCgiProtocolTest {
    private final JsonMapper json = new JsonMapper();
    private final HallCgiProtocol protocol = new HallCgiProtocol(json, null, new MissionCatalogPort() {
        @Override
        public List<Integer> ids() {
            return List.of(1563, 2662);
        }

        @Override
        public String title(int missionId) {
            return "任务 " + missionId;
        }
    });

    @Test
    void onlyStatusThreeIsRewardReady() throws Exception {
        var tasks = protocol.missions(json.readTree("""
                {"MissionInfo":[
                  {"MissionID":1563,"Status":2,"PeriodFinishCnt":0},
                  {"MissionID":2662,"Status":3,"PeriodFinishCnt":1}
                ]}
                """));

        assertThat(tasks).extracting("claimable").containsExactly(false, true);
    }

    @Test
    void mapsMissionCgiCodesToPlayerFacingMessages() {
        assertThat(protocol.claimFailureMessage(2662, new IllegalStateException("CGI result=1001: ")))
                .isEqualTo("任务尚未完成或当前不可领取");
        assertThat(protocol.claimFailureMessage(1563, new IllegalStateException("CGI result=6: ")))
                .contains("余额未达到补助条件");
    }
}
