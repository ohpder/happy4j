package cc.superbaby.happy4j.protocol;

import cc.superbaby.happy4j.protocol.game.MiniGameRoomProtocol;
import cc.superbaby.happy4j.protocol.schema.LotusPayloadCodec;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class MiniGameRoomProtocolTest {
    @Test
    void quickMatchSitDownIsTheExactTwelveByteWxpkgRequest() throws Exception {
        JsonMapper json = new JsonMapper();
        LotusPayloadCodec codec = new LotusPayloadCodec(ProtocolRegistry.loadDefault(), json);

        byte[] payload = codec.encodeJson("Native.HuanLeGameSvr.CRequestSitDownHGPara",
                json.writeValueAsString(MiniGameRoomProtocol.quickMatchSitDown(0)));

        assertThat(HexFormat.of().formatHex(payload))
                .isEqualTo("000000030000ffffffff0000");
    }
}
