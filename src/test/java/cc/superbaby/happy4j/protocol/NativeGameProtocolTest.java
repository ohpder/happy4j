package cc.superbaby.happy4j.protocol.wire;

import cc.superbaby.happy4j.protocol.generated.nativehlddz.CRequestPlayHGPara;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;

import com.google.protobuf.ByteString;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeGameProtocolTest {
    @Test
    void exposesRecoveredNativeGameMessagesAsStrongAndDynamicTypes() throws Exception {
        ProtocolRegistry registry = ProtocolRegistry.loadDefault();
        assertTrue(registry.messageCount() >= 1_300);
        assertEquals(4, registry.describe("Native.HuanLeGameSvr.CRequestPlayHGPara").size());

        CRequestPlayHGPara message = CRequestPlayHGPara.newBuilder()
                .setUin(0x11223344)
                .setTime(-123)
                .setPlayerDataLength(3)
                .setPlayData(ByteString.copyFromUtf8("ABC"))
                .build();
        String json = registry.decodeJson("Native.HuanLeGameSvr.CRequestPlayHGPara",
                message.toByteArray());
        assertTrue(json.contains("\"Uin\": 287454020"));
        assertTrue(json.contains("\"PlayData\": \"QUJD\""));
    }
}
