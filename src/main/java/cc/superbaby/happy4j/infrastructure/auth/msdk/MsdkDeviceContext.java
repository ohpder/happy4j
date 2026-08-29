package cc.superbaby.happy4j.infrastructure.auth.msdk;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Process-stable device envelope matching MSDKUtils.GetDeviceInfoWrapper.
 */
@Component
public final class MsdkDeviceContext {
    private final String uuid = UUID.randomUUID().toString().replace("-", "");

    public void enrichQrLogin(Map<String, Object> body) {
        Map<String, Object> device = new LinkedHashMap<>();
        device.put("uuid", uuid);
        device.put("device_language", "zh_CN");
        device.put("app_version", "8.056.016");
        device.put("screen_dpi", 420);
        device.put("resolution", "2400x1080");
        device.put("trade_mark", "Google");
        device.put("manufacturer", "Google");
        device.put("model", "sdk_gphone64_arm64");
        device.put("apn", "wifi");
        device.put("ram_info", "1974");
        device.put("rom_info", "");
        device.put("cpu_info", "arm64-v8a");
        device.put("imei", "");
        device.put("qimei", "");
        device.put("oaid", "");
        device.put("xid", "");
        device.put("qimei36", "");
        body.put("device_info", device);
        body.put("msdk_scenes", "QRCode");
        body.put("scenes_version", "1");
    }

    public String uuid() {
        return uuid;
    }
}
