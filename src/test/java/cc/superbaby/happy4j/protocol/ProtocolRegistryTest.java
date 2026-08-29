package cc.superbaby.happy4j.protocol.schema;

import cc.superbaby.happy4j.protocol.generated.tdrpb.postsvr.TMsg;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtocolRegistryTest {
    @Test
    void loadsCompleteRecoveredCatalogAndDynamicJsonCodec() throws Exception {
        ProtocolRegistry registry = ProtocolRegistry.loadDefault();
        assertTrue(registry.messageNames().size() >= 1_078);
        assertEquals(16, registry.message("Tdrpb.PostSvr.TMsg").getFields().size());

        byte[] binary = registry.encodeJson("Tdrpb.PostSvr.TMsg",
                "{\"miMsgId\":42,\"mUiMsgSrcId\":363,\"mAcMsgPara\":\"AQI=\"}");
        TMsg typed = TMsg.parseFrom(binary);
        assertEquals(42, typed.getMiMsgId());
        assertEquals(363, typed.getMUiMsgSrcId());
        assertTrue(registry.decodeJson("Tdrpb.PostSvr.TMsg", binary).contains("\"miMsgId\": 42"));
    }
}
