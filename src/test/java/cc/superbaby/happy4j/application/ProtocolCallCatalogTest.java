package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;
import cc.superbaby.happy4j.protocol.schema.LotusPayloadCodec;
import cc.superbaby.happy4j.protocol.mission.MissionStatusProtocol;
import cc.superbaby.happy4j.config.Happy4jProperties;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.time.Duration;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocolCallCatalogTest {
    @Test
    void everyActivityRequestEncodesAgainstRecoveredDescriptor() {
        GameCredential credential = new GameCredential(1234, 1, cc.superbaby.happy4j.domain.PlatformCredential.Provider.QQ, new byte[16], new byte[0], "en",
                "openid", "openkey", "pay", "pf", "pfkey", 1000, 3, Map.of());
        var registry = ProtocolRegistry.loadDefault();
        Happy4jProperties.Game game = new Happy4jProperties.Game(260, 64, 1,
                137887760, 10040714, "35", "legacy", "qq", "wx", "lotus", 443,
                Duration.ofSeconds(5), Duration.ofSeconds(8));
        var calls = new ProtocolCallCatalog(new JsonMapper(),
                new Happy4jProperties(null, game, null)).activityQueries(credential);
        assertThat(calls).hasSize(3);
        assertThat(calls.get(0).request().json()).contains("\"clientType\":64");
        calls.forEach(call -> assertThat(registry.encodeJson(call.request().requestType(), call.request().json())).isNotNull());
    }

    @Test
    void missionStatusMatchesJsExplicitIdListInsteadOfUnsupportedRangeScan() {
        GameCredential credential = new GameCredential(1234, 1,
                cc.superbaby.happy4j.domain.PlatformCredential.Provider.QQ,
                new byte[16], new byte[0], "en", "openid", "openkey", "pay", "pf", "pfkey",
                1000, 3, Map.of());
        Happy4jProperties.Game game = new Happy4jProperties.Game(260, 64, 1,
                137887760, 10040714, "35", "legacy", "qq", "wx", "lotus", 443,
                Duration.ofSeconds(5), Duration.ofSeconds(8));
        var request = new MissionStatusProtocol(new JsonMapper(),
                new Happy4jProperties(null, game, null)).fetchKnown(credential, java.util.List.of(1563, 2662));

        assertThat(request.destinationEntity()).isEqualTo(160);
        assertThat(request.json()).contains("\"missionIdNum\":2", "\"missionId\":[1563,2662]")
                .contains("\"beginMissionId\":0", "\"endMissionId\":0");
        assertThat(HexFormat.of().formatHex(new LotusPayloadCodec(ProtocolRegistry.loadDefault(), new JsonMapper())
                .encodeJson(request.requestType(), request.json())))
                .isEqualTo("000004d20000000001040000000000000040000000000000000000000002061b0a66"
                        + "000000000000");
    }
}
