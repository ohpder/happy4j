package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolRequest;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.protocol.wire.FunctionalEntity;
import cc.superbaby.happy4j.protocol.schema.LotusPayloadCodec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Declarative protocol catalog. New activity handlers require data, not transport changes.
 */
@Component
public final class ProtocolCallCatalog {
    private final ObjectMapper json;
    private final int clientType;
    private final int clientVersion;

    public ProtocolCallCatalog(ObjectMapper json, Happy4jProperties properties) {
        this.json = json;
        this.clientType = properties.game().clientType();
        this.clientVersion = properties.game().clientVersion();
    }

    /**
     * CLoginTransaction fires this right after the Lotus hall connects — a live device capture
     * shows it precedes every other hall/room call. The room server rejects CRequestLoginHGPara
     * with generic codes (10603/10832/10312) when this hasn't happened first.
     */
    public NamedCall missionLogin(GameCredential credential) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uin", credential.uin());
        body.put("transparent", 0);
        body.put("missionClinetType", clientType);
        body.put("versionNumber", clientVersion);
        body.put("flag", 0);
        body.put("abilitySet", 0);
        return named("mission-login", "任务系统登录", 7301, FunctionalEntity.MISSION_SERVER,
                "Tdrpb.MissionSvrMsgPara.CReqMissionLogin",
                "Tdrpb.MissionSvrMsgPara.CRspMissionLogin", body);
    }

    public List<NamedCall> activityQueries(GameCredential credential) {
        Map<String, Object> signIn = new LinkedHashMap<>();
        signIn.put("uin", credential.uin());
        signIn.put("accountType", credential.accountType());
        signIn.put("clientType", clientType);
        signIn.put("openid", credential.openId());
        signIn.put("command", 1);
        signIn.put("abilityFlags", 1);
        signIn.put("loginPlatform", credential.accountType() == 4 ? 8 : 0);

        Map<String, Object> buyBean = new LinkedHashMap<>();
        buyBean.put("uin", credential.uin());
        buyBean.put("accountType", credential.accountType());
        buyBean.put("clientType", clientType);
        buyBean.put("openId", credential.openId());
        buyBean.put("openKey", credential.openKey());
        buyBean.put("gameId", 260);
        buyBean.put("needUpdateTuringInfo", 1);

        Map<String, Object> competition = new LinkedHashMap<>();
        competition.put("userInfo", olympicUser(credential));
        competition.put("clientAbility", 0x3fff);
        competition.put("clientVersion", 52035584);
        competition.put("gameId", 260);
        competition.put("needEnrollStatus", 1);

        return List.of(
                named("sign-in", "每日签到", 18291, FunctionalEntity.LOGIC_SERVICE_SERVER,
                        "Tdrpb.HLDDZSignInLottery.SignInLotteryRequest",
                        "Tdrpb.HLDDZSignInLottery.SignInLotteryResponse", signIn),
                named("bean-discount", "欢乐豆优惠", 18436, FunctionalEntity.LOGIC_SERVICE_SERVER,
                        "Tdrpb.HLDDZBuyBeanDisCount.CReqBuyBeanDisCountQuery",
                        "Tdrpb.HLDDZBuyBeanDisCount.CRspBuyBeanDisCountQuery", buyBean),
                named("competition", "赛事活动", 16601, FunctionalEntity.OLYMPIC_SERVER,
                        "Tdrpb.HuanLeOlympicSvr.CReqFetchMatchList",
                        "Tdrpb.HuanLeOlympicSvr.CRspFetchMatchList", competition)
        );
    }

    public NamedCall claimSignIn(GameCredential credential, int command, int prizeIndex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uin", credential.uin());
        body.put("accountType", credential.accountType());
        body.put("clientType", clientType);
        body.put("openid", credential.openId());
        body.put("command", command);
        body.put("choosePrizeIndex", prizeIndex);
        body.put("abilityFlags", 1);
        body.put("loginPlatform", credential.accountType() == 4 ? 8 : 0);
        return named("sign-in", "领取签到奖励", 18291, FunctionalEntity.LOGIC_SERVICE_SERVER,
                "Tdrpb.HLDDZSignInLottery.SignInLotteryRequest",
                "Tdrpb.HLDDZSignInLottery.SignInLotteryResponse", body);
    }

    public NamedCall openTreasure(GameCredential credential, int itemId, int itemSeq) {
        byte[] open = credential.openId().getBytes(StandardCharsets.UTF_8);
        byte[] key = credential.openKey().getBytes(StandardCharsets.UTF_8);
        byte[] pay = credential.payToken().getBytes(StandardCharsets.UTF_8);
        byte[] pf = credential.pf().getBytes(StandardCharsets.UTF_8);
        byte[] pfKey = credential.pfKey().getBytes(StandardCharsets.UTF_8);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameId", 260);
        body.put("accountType", credential.accountType());
        body.put("clientType", clientType);
        body.put("openIdLen", open.length);
        body.put("openId", open);
        body.put("openKeyLen", key.length);
        body.put("openKey", key);
        body.put("payTokenLen", pay.length);
        body.put("payToken", pay);
        body.put("pfLen", pf.length);
        body.put("pf", pf);
        body.put("pfKeyLen", pfKey.length);
        body.put("pfKey", pfKey);
        body.put("itemId", itemId);
        body.put("itemSeq", itemSeq);
        return named("treasure", "打开宝箱", 17018, FunctionalEntity.MISSION_SERVER,
                "Tdrpb.HLMissionTreasureRewardPara.CReqHLOpenTreasureBoxPara",
                "Tdrpb.HLMissionTreasureRewardPara.CRspHLOpenTreasureBoxPara", body);
    }

    public NamedCall itemInventory(GameCredential credential, List<Integer> itemIds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("uin", credential.uin());
        body.put("appId", 260);
        body.put("clientType", clientType);
        body.put("accountType", credential.accountType());
        body.put("itemIds", itemIds == null ? List.of() : List.copyOf(itemIds));
        body.put("requestFlags", 4); // SupportMax1024Item, matching wxpkg wab.Boe
        return named("inventory", "道具背包", 15903, FunctionalEntity.APP_ITEM_SERVER,
                LotusPayloadCodec.ITEM_QUERY_REQ, LotusPayloadCodec.ITEM_QUERY_RSP, body);
    }

    private Map<String, Object> olympicUser(GameCredential c) {
        byte[] openId = c.openId().getBytes(StandardCharsets.UTF_8);
        byte[] openKey = c.openKey().getBytes(StandardCharsets.UTF_8);
        byte[] pfKey = c.pfKey().getBytes(StandardCharsets.UTF_8);
        byte[] pf = c.pf().getBytes(StandardCharsets.UTF_8);
        byte[] pay = c.payToken().getBytes(StandardCharsets.UTF_8);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameId", 260);
        body.put("clientType", clientType);
        body.put("accountType", c.accountType());
        body.put("openIdLen", openId.length);
        body.put("openId", openId);
        body.put("openKeyLen", openKey.length);
        body.put("openKey", openKey);
        body.put("pfKeyLen", pfKey.length);
        body.put("pfKey", pfKey);
        body.put("pfLen", pf.length);
        body.put("pf", pf);
        body.put("payTokenLen", pay.length);
        body.put("payToken", pay);
        return body;
    }

    private NamedCall named(String id, String title, int messageId, int destination,
                            String request, String response, Map<String, Object> body) {
        try {
            return new NamedCall(id, title, new ProtocolRequest(messageId, destination, (short) -1,
                    request, response, json.writeValueAsString(body), true));
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    public record NamedCall(String id, String title, ProtocolRequest request) {
    }
}
