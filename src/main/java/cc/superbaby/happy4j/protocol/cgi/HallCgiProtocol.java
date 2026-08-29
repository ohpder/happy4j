package cc.superbaby.happy4j.protocol.cgi;

import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolRequest;
import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolResponse;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.MissionTask;
import cc.superbaby.happy4j.domain.PlayerProfile;
import cc.superbaby.happy4j.protocol.wire.FunctionalEntity;
import cc.superbaby.happy4j.application.port.MissionCatalogPort;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * wxpkg $ra/bsa CGI-over-Lotus protocol used for profile and common missions.
 */
@Component
public final class HallCgiProtocol {
    public static final int PROFILE_QQ = 1028;
    public static final int PROFILE_WECHAT = 1029;
    public static final int MISSION_REWARD = 32791;
    public static final int BANKRUPT_SUPPORT_MISSION = 1563;
    private static final String REQUEST = "Tdrpb.HttpOutSvr.CSimpleHttpRequestMsgPara";
    private static final String RESPONSE = "Tdrpb.HttpOutSvr.CSimpleHttpResponseMsgPara";

    private final ObjectMapper json;
    private final Happy4jProperties properties;
    private final MissionCatalogPort missionCatalog;

    public HallCgiProtocol(ObjectMapper json, Happy4jProperties properties, MissionCatalogPort missionCatalog) {
        this.json = json;
        this.properties = properties;
        this.missionCatalog = missionCatalog;
    }

    public ProtocolRequest profile(GameCredential credential) {
        if (credential.provider() == cc.superbaby.happy4j.domain.PlatformCredential.Provider.WECHAT) {
            String get = "urlindex=1&happyddztype=1&getparastart&simple_get_token=1&access_token="
                    + credential.openKey() + "&getparaend&postparastart&{\"openidlist\":[{\"openid\":\""
                    + credential.openId() + "\"}]}&postparaend";
            return request(credential, PROFILE_WECHAT, get, "");
        }
        String post = "{\"accessToken\":\"" + escape(credential.openKey()) + "\","
                + "\"appid\":" + properties.auth().qq().thirdAppId() + ","
                + "\"openid\":\"" + escape(credential.openId()) + "\"}";
        return request(credential, PROFILE_QQ, "fetchtype=100", post);
    }

    public ProtocolRequest missions(GameCredential credential) {
        StringBuilder get = new StringBuilder(common(credential)).append("&cmd=get&missionidnum=")
                .append(missionCatalog.ids().size());
        for (int i = 0; i < missionCatalog.ids().size(); i++)
            get.append("&missionid").append(i).append('=').append(missionCatalog.ids().get(i));
        return request(credential, MISSION_REWARD, get.toString(), "");
    }

    public ProtocolRequest claimMission(GameCredential credential, int missionId) {
        return request(credential, MISSION_REWARD,
                common(credential) + "&cmd=submit&missionid=" + missionId, "");
    }

    public JsonNode response(ProtocolResponse response) {
        try {
            JsonNode envelope = json.readTree(response.json());
            int result = envelope.path("ResultID").asInt();
            if (result != 0) throw new IllegalStateException("CGI tunnel result=" + result);
            byte[] bytes = Base64.getDecoder().decode(envelope.path("ResponseContent").asString());
            String text = new String(bytes, StandardCharsets.UTF_8);
            int start = text.indexOf('{');
            if (start < 0) throw new IllegalStateException("CGI response is not JSON: " + text);
            JsonNode root = json.readTree(text.substring(start));
            int cgiRet = integer(root, "CgiRet", "result", "ret");
            if (cgiRet != 0) throw new IllegalStateException("CGI result=" + cgiRet + ": "
                    + string(root, "resultstr", "message", "msg"));
            return root;
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalStateException("CGI response decode failed", failure);
        }
    }

    public PlayerProfile profile(JsonNode root, GameCredential credential) {
        JsonNode value = root;
        if (root.path("profilelist").isArray() && !root.path("profilelist").isEmpty())
            value = root.path("profilelist").get(0);
        String nickname = string(value, "nickName", "nickname", "nick");
        String avatar = string(value, "picture100", "picture", "avatarUrl", "headUrl");
        if (nickname.isBlank()) nickname = raw(credential, "nickname", "nick", "strNick");
        if (avatar.isBlank()) avatar = raw(credential, "avatarUrl", "headUrl", "picture");
        return new PlayerProfile(nickname, avatar, integer(value, "sex", "gender"), Instant.now());
    }

    public List<MissionTask> missions(JsonNode root) {
        JsonNode values = first(root, "MissionInfo", "missionInfo", "missions");
        if (values == null || !values.isArray()) return List.of();
        List<MissionTask> result = new ArrayList<>();
        for (JsonNode value : values) {
            int id = integer(value, "MissionID", "missionId", "mission_id");
            if (id <= 0) continue;
            int status = integer(value, "Status", "status");
            int count = integer(value, "PeriodFinishCnt", "periodFinishCount", "period_finish_count");
            result.add(new MissionTask(id, missionCatalog.title(id), status, count,
                    status == 3, value.toString(), Instant.now()));
        }
        return List.copyOf(result);
    }

    public String claimFailureMessage(int missionId, Throwable failure) {
        String message = rootMessage(failure);
        java.util.regex.Matcher result = java.util.regex.Pattern.compile("CGI result=(\\d+)").matcher(message);
        if (!result.find()) return message;
        int code = Integer.parseInt(result.group(1));
        if (missionId == BANKRUPT_SUPPORT_MISSION && code == 6)
            return "欢乐豆补助暂不可领取：余额未达到补助条件、奖励已领取或已过期";
        if (code == 1001) return "任务尚未完成或当前不可领取";
        return "奖励领取失败，请刷新任务状态后重试（错误码 " + code + "）";
    }

    private static String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null) root = root.getCause();
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private ProtocolRequest request(GameCredential credential, int cgiId, String get, String post) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("Uin", credential.uin());
            body.put("IsNoNeedRsp", 0);
            body.put("IsHTTPS", 0);
            body.put("URL", "");
            body.put("CGIPostPara", post);
            body.put("Cookie", cookie(credential));
            body.put("TransparentID", 0);
            body.put("CgiID", cgiId);
            body.put("CGIGetPara", get);
            body.put("ReqParaZipFlag", 0);
            body.put("RspParaZipFlag", 0);
            body.put("GameID", 0);
            body.put("SrcClientType", 3);
            return new ProtocolRequest(15502, FunctionalEntity.HTTP_OUT_SERVER, (short) 0,
                    REQUEST, RESPONSE, json.writeValueAsString(body), true);
        } catch (Exception failure) {
            throw new IllegalStateException("CGI request encode failed", failure);
        }
    }

    private String common(GameCredential c) {
        return "uin=" + c.uin() + "&accounttype=" + c.accountType() + "&gameid="
                + properties.game().id() + "&clienttype=" + properties.game().clientType()
                + "&openid=" + c.openId();
    }

    private String cookie(GameCredential c) {
        String key = c.openKey();
        return "webskey=" + raw(c, "webskey", "webSkey") + "; happy_webskey="
                + raw(c, "happy_webskey", "happyWebskey") + "; accounttype=" + c.accountType()
                + "; clienttype=" + properties.game().clientType() + "; happy_uin=" + c.uin()
                + "; gameid=" + properties.game().id() + "; accesstoken=" + key + "; openkey=" + key
                + "; pfkey=" + c.pfKey() + "; appid=" + properties.auth().qq().thirdAppId()
                + "; channelid=" + properties.game().channelId() + "; happy_skey="
                + raw(c, "happy_skey", "skey") + "; paytoken=" + c.payToken() + "; openid=" + c.openId();
    }

    private static JsonNode first(JsonNode root, String... names) {
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null) return value;
        }
        return null;
    }

    private static int integer(JsonNode root, String... names) {
        JsonNode value = first(root, names);
        return value == null ? 0 : value.asInt();
    }

    private static String string(JsonNode root, String... names) {
        JsonNode value = first(root, names);
        return value == null ? "" : value.asString("");
    }

    private static String raw(GameCredential credential, String... names) {
        for (String name : names)
            for (var entry : credential.raw().entrySet())
                if (entry.getKey().equalsIgnoreCase(name) && entry.getValue() != null)
                    return entry.getValue().toString();
        return "";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
