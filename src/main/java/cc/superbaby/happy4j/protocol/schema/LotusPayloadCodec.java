package cc.superbaby.happy4j.protocol.schema;

import cc.superbaby.happy4j.protocol.wire.ProtocolException;
import cc.superbaby.happy4j.protocol.wire.TdrBufferReader;
import cc.superbaby.happy4j.protocol.wire.TdrBufferWriter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lotus payload codec for the live TDR path.  The generated classes implement
 * ITdrPackUnPack; their PBPackTo methods are fixed-width TDR, not protobuf wire data.
 * Unknown schemas deliberately retain the descriptor-driven protobuf fallback.
 */
public final class LotusPayloadCodec {
    private static final String SIGN_IN_REQ = "Tdrpb.HLDDZSignInLottery.SignInLotteryRequest";
    private static final String SIGN_IN_RSP = "Tdrpb.HLDDZSignInLottery.SignInLotteryResponse";
    private static final String BEAN_REQ = "Tdrpb.HLDDZBuyBeanDisCount.CReqBuyBeanDisCountQuery";
    private static final String BEAN_RSP = "Tdrpb.HLDDZBuyBeanDisCount.CRspBuyBeanDisCountQuery";
    private static final String OLYMPIC_REQ = "Tdrpb.HuanLeOlympicSvr.CReqFetchMatchList";
    private static final String OLYMPIC_RSP = "Tdrpb.HuanLeOlympicSvr.CRspFetchMatchList";
    private static final String QUICK_ROOM_REQ = "Tdrpb.AppQuickGameSvr.CReqRecommandRoom";
    private static final String QUICK_ROOM_RSP = "Tdrpb.AppQuickGameSvr.CRspRecommandRoom";
    private static final String APP_DIR_REQ = "Native.AppDir.CReqAppDirStuffPara";
    private static final String APP_DIR_RSP = "Native.AppDir.CRspAppDirStuffPara";
    private static final String GAME_LOGIN_REQ = "Native.HuanLeGameSvr.CRequestLoginHGPara";
    private static final String GAME_LOGIN_RSP = "Native.HuanLeGameSvr.CResponseLoginHGPara";
    private static final String GAME_SIT_REQ = "Native.HuanLeGameSvr.CRequestSitDownHGPara";
    private static final String GAME_SIT_RSP = "Native.HuanLeGameSvr.CResponseSitDownHGPara";
    private static final String GAME_STAND_UP_REQ = "Native.HuanLeGameSvr.CRequestStandUpHGPara";
    private static final String GAME_STAND_UP_RSP = "Native.HuanLeGameSvr.CResponseStandUpHGPara";
    private static final String GAME_READY_REQ = "Native.HuanLeGameSvr.CRequestReadyHGPara";
    private static final String GAME_READY_RSP = "Native.HuanLeGameSvr.CResponseReadyHGPara";
    private static final String GAME_PLAY_REQ = "Native.HuanLeGameSvr.CRequestPlayHGPara";
    private static final String GAME_PLAY_RSP = "Native.HuanLeGameSvr.CResponsePlayHGPara";
    private static final String GAME_SVR_INFO_REQ = "Native.HuanLeGameSvr.CRequestSvrInfoHGPara";
    private static final String GAME_SVR_INFO_RSP = "Native.HuanLeGameSvr.CResponseSvrInfoHGPara";
    private static final String CGI_REQ = "Tdrpb.HttpOutSvr.CSimpleHttpRequestMsgPara";
    private static final String CGI_RSP = "Tdrpb.HttpOutSvr.CSimpleHttpResponseMsgPara";
    private static final String TREASURE_REQ = "Tdrpb.HLMissionTreasureRewardPara.CReqHLOpenTreasureBoxPara";
    private static final String TREASURE_RSP = "Tdrpb.HLMissionTreasureRewardPara.CRspHLOpenTreasureBoxPara";
    private static final String MISSION_STATUS_RSP = "Tdrpb.MissionSvrMsgPara.CRspFetchMissionStatus";
    private static final String MISSION_STATUS_REQ = "Tdrpb.MissionSvrMsgPara.CReqFetchMissionStatus";
    public static final String ITEM_QUERY_REQ = "Tdrpb.AppItemSvr.CReqQueryItemInfo";
    public static final String ITEM_QUERY_RSP = "Tdrpb.AppItemSvr.CRspQueryItemInfo";

    private final ProtocolRegistry protobuf;
    private final ObjectMapper json;

    public LotusPayloadCodec(ProtocolRegistry protobuf, ObjectMapper json) {
        this.protobuf = protobuf;
        this.json = json;
    }

    public byte[] encodeJson(String schema, String source) {
        try {
            JsonNode root = json.readTree(source);
            return switch (schema) {
                case SIGN_IN_REQ -> encodeSignIn(root);
                case BEAN_REQ -> encodeBeanQuery(root);
                case OLYMPIC_REQ -> encodeMatchList(root);
                case QUICK_ROOM_REQ -> encodeRecommendRoom(root);
                case APP_DIR_REQ -> encodeAppDirStuff(root);
                case GAME_LOGIN_REQ -> encodeGameLogin(root);
                case GAME_SIT_REQ -> encodeGameSitDown(root);
                case GAME_STAND_UP_REQ -> encodeGameStandUp(root);
                case GAME_READY_REQ -> encodeGameReady(root);
                case GAME_PLAY_REQ -> encodeGamePlay(root);
                case GAME_SVR_INFO_REQ -> encodeGameSvrInfo(root);
                case CGI_REQ -> encodeCgiRequest(root);
                case TREASURE_REQ -> encodeTreasure(root);
                case MISSION_STATUS_REQ -> encodeMissionStatus(root);
                case ITEM_QUERY_REQ -> encodeItemQuery(root);
                default -> protobuf.encodeJson(schema, source);
            };
        } catch (IOException failure) {
            throw new ProtocolException("invalid JSON for " + schema, failure);
        }
    }

    public String decodeJson(String schema, byte[] payload) {
        try {
            Object value = switch (schema) {
                case SIGN_IN_RSP -> decodeSignIn(payload);
                case BEAN_RSP -> decodeBeanQuery(payload);
                case OLYMPIC_RSP -> decodeMatchList(payload);
                case QUICK_ROOM_RSP -> decodeRecommendRoom(payload);
                case APP_DIR_RSP -> decodeAppDirStuff(payload);
                case GAME_LOGIN_RSP -> decodeGameLogin(payload);
                case GAME_SIT_RSP -> decodeGameSitDown(payload);
                case GAME_STAND_UP_RSP -> decodeGameStandUp(payload);
                case GAME_READY_RSP -> decodeGameReady(payload);
                case GAME_PLAY_RSP -> decodeGamePlay(payload);
                case GAME_SVR_INFO_RSP -> decodeGameSvrInfoIfTdr(payload);
                case CGI_RSP -> decodeCgiResponse(payload);
                case TREASURE_RSP -> decodeTreasure(payload);
                case MISSION_STATUS_RSP -> decodeMissionStatusesIfTdr(payload);
                case ITEM_QUERY_RSP -> decodeItemQuery(payload);
                default -> null;
            };
            return value == null ? protobuf.decodeJson(schema, payload) : json.writeValueAsString(value);
        } catch (RuntimeException failure) {
            return raw(schema, payload, failure.getMessage());
        }
    }

    private static byte[] encodeSignIn(JsonNode root) {
        TdrBufferWriter out = new TdrBufferWriter();
        out.writeInt32(unsignedInt32(root, "uin"));
        out.writeInt32(integer(root, "accountType", "account_type"));
        out.writeInt32(integer(root, "clientType", "client_type"));
        writeString(out, text(root, "openid"), 128);
        out.writeInt16(integer(root, "command"));
        out.writeInt16(integer(root, "choosePrizeIndex", "choose_prize_index"));
        out.writeInt32(integer(root, "abilityFlags", "ability_flags"));
        out.writeInt32(integer(root, "loginPlatform", "login_platform"));
        out.writeInt32(integer(root, "isHasGDTExtraPrize", "is_has_gdt_extra_prize"));
        out.writeInt32(integer(root, "watchAdvExtraPrizeCnt", "watch_adv_extra_prize_cnt"));
        return out.toByteArray();
    }

    private static byte[] encodeBeanQuery(JsonNode root) {
        TdrBufferWriter out = new TdrBufferWriter();
        out.writeInt32(unsignedInt32(root, "uin"));
        out.writeInt32(integer(root, "gameId", "game_id"));
        out.writeInt32(integer(root, "accountType", "account_type"));
        out.writeInt32(integer(root, "clientType", "client_type"));
        writeString(out, text(root, "openId", "open_id"), 1024);
        writeString(out, text(root, "openKey", "open_key"), 1024);
        out.writeInt8(integer(root, "needUpdateTuringInfo", "need_update_turing_info"));
        return out.toByteArray();
    }

    /** wxpkg wab request used by cmdFetchPlayItemInfo (15903 -> entity 159). */
    private static byte[] encodeItemQuery(JsonNode root) {
        JsonNode ids = field(root, "itemIds", "item_ids");
        int count = ids != null && ids.isArray() ? ids.size() : 0;
        if (count > 512) throw new ProtocolException("item query exceeds 512 ids");
        TdrBufferWriter out = new TdrBufferWriter(32 + count * Integer.BYTES);
        out.writeInt16(0); // old transparent bytes
        out.writeInt32(unsignedInt32(root, "uin"));
        out.writeInt32(integer(root, "appId", "app_id"));
        out.writeInt32(integer(root, "clientType", "client_type"));
        out.writeInt32(integer(root, "accountType", "account_type"));
        out.writeInt16(count);
        for (int index = 0; index < count; index++) out.writeInt32(ids.get(index).asInt());
        out.writeInt32(integer(root, "requestFlags", "request_flags"));
        out.writeInt16(0); // transparent bytes
        return out.toByteArray();
    }

    private static byte[] encodeMatchList(JsonNode root) throws IOException {
        TdrBufferWriter out = new TdrBufferWriter();
        JsonNode user = field(root, "userInfo", "user_info");
        encodeOlympicUser(out, user == null ? root : user);
        out.writeInt32(integer(root, "clientAbility", "client_ability"));
        out.writeInt32(integer(root, "clientVersion", "client_version"));
        out.writeInt32(integer(root, "seriesId", "series_id"));
        out.writeInt32(integer(root, "gameId", "game_id"));
        out.writeInt32(integer(root, "matchId", "match_id"));
        out.writeInt32(integer(root, "needEnrollStatus", "need_enroll_status"));
        out.writeInt32(integer(root, "channelId", "channel_id"));
        return out.toByteArray();
    }

    /**
     * CQuickSvrCommand::RequestQuickGame + CReqRecommandRoom::pack.
     */
    private static byte[] encodeRecommendRoom(JsonNode root) throws IOException {
        JsonNode search = field(root, "searchInfo", "search_info");
        if (search == null) search = root;
        JsonNode union = field(search, "searchTypeDefinedCondition", "search_type_defined_condition");
        JsonNode condition = union == null ? null : field(union, "searchCondition", "search_condition");
        if (condition == null) condition = search;

        byte[] transparent = bytes(root, "transplatomBuffer", "transplatom_buffer");
        if (transparent.length > 128)
            throw new ProtocolException("quick transparent buffer exceeds 128 bytes");
        int gameId = integer(search, "gameId", "game_id");
        int appId = integer(search, "appId", "app_id");
        if (appId == 0) appId = gameId;
        int expectAppId = integer(condition, "expectAppId", "expect_app_id");
        if (expectAppId == 0) expectAppId = gameId;

        TdrBufferWriter out = new TdrBufferWriter(192);
        out.writeInt8(transparent.length).writeBytes(transparent);
        out.writeInt32(integer(search, "clientVersion", "client_version"));
        out.writeInt32(integer(search, "clientType", "client_type"));
        out.writeInt32(appId);
        out.writeInt16(gameId);
        out.writeInt8(integer(search, "miniOnlinePercent", "mini_online_percent"));
        out.writeInt8(integer(search, "maxOnlinePercent", "max_online_percent"));
        out.writeInt32(integer(search, "searchType", "search_type"));
        out.writeInt8(12); // packed size of TSearchCondition
        out.writeInt32(integer(condition, "sceneId", "scene_id"));
        out.writeInt32(expectAppId);
        out.writeInt16(integer(condition, "svrId", "svr_id"));
        out.writeInt16(integer(condition, "roomId", "room_id"));
        out.writeInt16(0); // legacy trans_buf length
        out.writeCStringFixed(text(root, "openId", "open_id"), 128);
        out.writeInt32(unsignedInt32(root, "uin"));
        writeString(out, "", 128); // current trailing quick-game extension
        return out.toByteArray();
    }

    /**
     * CAppDirCommand::ReqStuff for the downloadable TSDK server lists.
     */
    private static byte[] encodeAppDirStuff(JsonNode root) {
        int stuffId = integer(root, "stuffId", "stuff_id");
        int gameId = integer(root, "gameId", "game_id");
        int clientType = integer(root, "clientType", "client_type");
        TdrBufferWriter stuff = new TdrBufferWriter(46);
        stuff.writeInt16(stuffId).writeInt16(2).writeInt32(1).writeInt16(2)
                .writeInt32(gameId).writeInt32(clientType)
                .writeBytes(new byte[16])
                .writeInt16(0).writeInt16(0).writeInt32(0).writeInt32(0);

        TdrBufferWriter out = new TdrBufferWriter(68);
        out.writeInt16(0); // request identifier length
        out.writeInt32(integer(root, "accountType", "account_type"));
        out.writeInt32((int) (System.currentTimeMillis() / 1000));
        out.writeInt16(1); // one TStuffTLV
        out.writeInt32(10001).writeInt32(stuff.size()).writeBytes(stuff.toByteArray());
        out.writeInt16(clientType);
        return out.toByteArray();
    }

    /**
     * Native HuanLeGameSvr::CRequestLoginHGPara::pack (TDR, not protobuf).
     */
    private static byte[] encodeGameLogin(JsonNode root) throws IOException {
        TdrBufferWriter out = new TdrBufferWriter(256);
        out.writeInt32(unsignedInt32(root, "Uin", "uin"));
        out.writeInt16(integer(root, "RequestSrc", "request_src"));
        out.writeInt32(integer(root, "LoginLifeStyle", "login_life_style"));
        out.writeInt32(integer(root, "RoomID", "room_id"));
        JsonNode clientInfo = field(root, "ClientInfo", "client_info");
        encodeGameClientInfo(out, clientInfo == null ? root : clientInfo);
        encodeTlvList(out, root);
        return out.toByteArray();
    }

    /**
     * Native HuanLeGameSvr::TClientInfo::pack. Referenced buffers carry an i16 length.
     */
    private static void encodeGameClientInfo(TdrBufferWriter out, JsonNode root) throws IOException {
        byte[] nick = bytes(root, "Nick", "nick");
        writeReferencedBytes(out, integerOrLength(root, nick, "NickLen", "nick_len"), nick, 64);
        out.writeInt8(integer(root, "Age", "age"));
        out.writeInt8(integer(root, "Gender", "gender"));
        out.writeInt16(integer(root, "AccountType", "account_type"));
        out.writeInt16(integer(root, "ClientType", "client_type"));
        out.writeInt64(longInteger(root, "PFKey", "pf_key"));
        out.writeInt16(integer(root, "Network", "network"));
        out.writeInt16(integer(root, "Operator", "operator"));
        out.writeInt32(integer(root, "Channel", "channel"));
        out.writeInt32(integer(root, "ClientVersion", "client_version"));
        writeGameReferencedBytes(out, root, 128, "OSVersionlLen", "os_versionl_len", "OSVersion", "os_version");
        writeGameReferencedBytes(out, root, 64, "TerminalLen", "terminal_len", "Terminal", "terminal");
        writeGameReferencedBytes(out, root, 128, "OpenIDLen", "open_id_len", "OpenID", "open_id");
        writeGameReferencedBytes(out, root, 256, "HeadUrlLen", "head_url_len", "HeadUrl", "head_url");
    }

    private static void writeGameReferencedBytes(TdrBufferWriter out, JsonNode root, int max,
                                                 String lengthCamel, String lengthSnake,
                                                 String valueCamel, String valueSnake) throws IOException {
        byte[] value = bytes(root, valueCamel, valueSnake);
        writeReferencedBytes(out, integerOrLength(root, value, lengthCamel, lengthSnake), value, max);
    }

    private static byte[] encodeGameSitDown(JsonNode root) throws IOException {
        TdrBufferWriter out = new TdrBufferWriter(16);
        out.writeInt32(integer(root, "SitDownFlag", "sit_down_flag"));
        out.writeInt16(integer(root, "RoomID", "room_id"));
        out.writeInt16(integer(root, "TableID", "table_id"));
        out.writeInt16(integer(root, "SeatID", "seat_id"));
        encodeTlvList(out, root);
        return out.toByteArray();
    }

    /**
     * wxpkg nja: StandUpFlag is i32 and RoomID is i16.
     */
    private static byte[] encodeGameStandUp(JsonNode root) {
        return new TdrBufferWriter(6)
                .writeInt32(integer(root, "StandUpFlag", "stand_up_flag"))
                .writeInt16(integer(root, "RoomID", "room_id"))
                .toByteArray();
    }

    private static byte[] encodeGameReady(JsonNode root) throws IOException {
        TdrBufferWriter out = new TdrBufferWriter(12);
        out.writeInt16(integer(root, "ReadyMode", "ready_mode"));
        out.writeInt16(integer(root, "RoomID", "room_id"));
        out.writeInt16(integer(root, "TableID", "table_id"));
        encodeTlvList(out, root);
        return out.toByteArray();
    }

    private static byte[] encodeGamePlay(JsonNode root) throws IOException {
        byte[] playData = bytes(root, "PlayData", "play_data");
        TdrBufferWriter out = new TdrBufferWriter(10 + playData.length);
        out.writeInt32(unsignedInt32(root, "Uin", "uin"));
        out.writeInt32(integer(root, "Time", "time"));
        writeReferencedBytes(out, integerOrLength(root, playData,
                "PlayerDataLength", "player_data_length"), playData, 1024);
        return out.toByteArray();
    }

    private static byte[] encodeGameSvrInfo(JsonNode root) {
        // Current wxpkg Qqa uses Pqa protobuf even though the surrounding GameSvr channel and
        // login/sit messages use native TDR. Observed native 0002 was silently discarded.
        return new TdrBufferWriter(2).writeInt8(0x08)
                .writeInt8(integer(root, "InfoType", "info_type")).toByteArray();
    }

    /**
     * Exact wxpkg Xra layout used by $ra/bsa for message 15502.  It is newer than the
     * descriptor's protobuf view and carries the CGI query/post/cookie as native TDR bytes.
     */
    private static byte[] encodeCgiRequest(JsonNode root) throws IOException {
        byte[] url = bytes(root, "URL", "url");
        byte[] post = bytes(root, "CGIPostPara", "cgi_post_para");
        byte[] cookie = bytes(root, "Cookie", "cookie");
        byte[] get = bytes(root, "CGIGetPara", "cgi_get_para");
        if (url.length > 4096 || post.length > 10240 || cookie.length > 10240 || get.length > 1024)
            throw new ProtocolException("CGI request field exceeds wxpkg Xra limit");
        TdrBufferWriter out = new TdrBufferWriter(48 + url.length + post.length + cookie.length + get.length);
        out.writeInt32(unsignedInt32(root, "Uin", "uin"));
        out.writeInt8(integer(root, "IsNoNeedRsp", "is_no_need_rsp"));
        out.writeInt8(integer(root, "IsHTTPS", "is_https"));
        out.writeInt16(url.length).writeBytes(url);
        out.writeInt16(post.length).writeBytes(post);
        out.writeInt16(cookie.length).writeBytes(cookie);
        out.writeInt32(integer(root, "TransparentID", "transparent_id"));
        out.writeInt32(integer(root, "CgiID", "cgi_id"));
        out.writeInt16(get.length).writeBytes(get);
        out.writeInt8(integer(root, "ReqParaZipFlag", "req_para_zip_flag"));
        out.writeInt8(integer(root, "RspParaZipFlag", "rsp_para_zip_flag"));
        out.writeInt16(0); // Vra header short-array count
        out.writeInt32(integer(root, "GameID", "game_id"));
        out.writeInt32(integer(root, "SrcClientType", "src_client_type") == 0
                ? 3 : integer(root, "SrcClientType", "src_client_type"));
        out.writeInt16(0); // Wra header key/value array count
        return out.toByteArray();
    }

    private static void encodeTlvList(TdrBufferWriter out, JsonNode root) throws IOException {
        JsonNode values = field(root, "TLVInfo", "tlv_info");
        int count = values == null || !values.isArray() ? integer(root, "TLVNum", "tlv_num") : values.size();
        if (count < 0 || count > 32)
            throw new ProtocolException("game TLV count outside 0..32: " + count);
        if (count != 0 && (values == null || !values.isArray() || values.size() != count)) {
            throw new ProtocolException("game TLVNum does not match TLVInfo");
        }
        out.writeInt16(count);
        for (int index = 0; index < count; index++) {
            JsonNode value = values.get(index);
            byte[] bytes = bytes(value, "Value", "value");
            out.writeInt16(integer(value, "Type", "type"));
            writeReferencedBytes(out, integerOrLength(value, bytes, "Len", "len"), bytes, 0x7fff);
        }
    }

    private static void encodeOlympicUser(TdrBufferWriter out, JsonNode root) throws IOException {
        out.writeInt32(integer(root, "gameId", "game_id"));
        byte[] nick = bytes(root, "nick");
        writeReferencedBytes(out, integerOrLength(root, nick, "nickLen", "nick_len"), nick, 64);
        out.writeInt32(integer(root, "clientType", "client_type"));
        out.writeInt32(integer(root, "accountType", "account_type"));
        byte[] openId = bytes(root, "openId", "open_id");
        writeReferencedBytes(out, integerOrLength(root, openId, "openIdLen", "open_id_len"), openId, 1024);
        byte[] openKey = bytes(root, "openKey", "open_key");
        writeReferencedBytes(out, integerOrLength(root, openKey, "openKeyLen", "open_key_len"), openKey, 1024);
        byte[] pfKey = bytes(root, "pfKey", "pf_key");
        writeReferencedBytes(out, integerOrLength(root, pfKey, "pfKeyLen", "pf_key_len"), pfKey, 1024);
        byte[] pf = bytes(root, "pf");
        writeReferencedBytes(out, integerOrLength(root, pf, "pfLen", "pf_len"), pf, 1024);
        byte[] pay = bytes(root, "payToken", "pay_token");
        writeReferencedBytes(out, integerOrLength(root, pay, "payTokenLen", "pay_token_len"), pay, 1024);
    }

    private static byte[] encodeTreasure(JsonNode root) throws IOException {
        TdrBufferWriter out = new TdrBufferWriter();
        out.writeInt32(integer(root, "gameId", "game_id"));
        out.writeInt32(integer(root, "accountType", "account_type"));
        out.writeInt32(integer(root, "clientType", "client_type"));
        writeLengthAndBytes(out, root, 1024, "openId", "open_id");
        writeLengthAndBytes(out, root, 1024, "openKey", "open_key");
        writeLengthAndBytes(out, root, 1024, "payToken", "pay_token");
        writeLengthAndBytes(out, root, 1024, "pf", "pf");
        writeLengthAndBytes(out, root, 1024, "pfKey", "pf_key");
        out.writeInt32(integer(root, "itemId", "item_id"));
        out.writeInt32(integer(root, "itemSeq", "item_seq"));
        return out.toByteArray();
    }

    private static void writeLengthAndBytes(TdrBufferWriter out, JsonNode root, int max,
                                            String camel, String snake) throws IOException {
        byte[] value = bytes(root, camel, snake);
        int length = integerOrLength(root, value, camel + "Len", snake + "_len");
        writeReferencedBytes(out, length, value, max);
    }

    private static Map<String, Object> decodeBeanQuery(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", in.readInt32());
        result.put("resultStr", readString(in, 1024));
        result.put("turingUserType", readString(in, 1024));
        result.put("huanleUserType", readString(in, 1024));
        result.put("hasDiscount", in.readInt32());
        appendRemaining(result, in);
        return result;
    }

    /** wxpkg xab/vab response: result, uin, item array and server time. */
    private static Map<String, Object> decodeItemQuery(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        int transparentLength = in.readUInt16();
        result.put("transparentBase64", Base64.getEncoder().encodeToString(in.readBytes(transparentLength)));
        result.put("resultId", in.readInt32());
        result.put("uin", in.readUInt32());
        int count = in.readUInt16();
        if (count > 1024) throw new ProtocolException("item response exceeds 1024 entries");
        List<Map<String, Object>> items = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int sequence = in.readInt32();
            int itemId = in.readInt32();
            int itemCount = in.readInt32();
            int buyTime = in.readInt32();
            int startTime = in.readInt32();
            int validTime = in.readInt32();
            int reservedLength = in.readUInt16();
            byte[] reserved = in.readBytes(reservedLength);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sequence", sequence);
            item.put("itemId", itemId);
            item.put("count", itemCount);
            item.put("buyTime", buyTime);
            item.put("startTime", startTime);
            item.put("validTime", validTime);
            item.put("reservedBase64", Base64.getEncoder().encodeToString(reserved));
            items.add(item);
        }
        result.put("items", items);
        result.put("serverTime", in.readUInt32());
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeSignIn(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", in.readInt32());
        result.put("mode", in.readInt16());
        result.put("state", in.readInt16());
        result.put("totalSignInDayCount", in.readInt32());
        result.put("currentSignInDayCount", in.readInt16());
        result.put("remainSignInDayCount", in.readInt16());
        result.put("getPrizeIndex", in.readInt16());
        int prizeCount = in.readUInt16();
        result.put("prizeInfoCount", prizeCount);
        List<Map<String, Integer>> prizes = new ArrayList<>();
        for (int i = 0; i < Math.min(prizeCount, 5); i++) {
            prizes.add(Map.of("state", in.readInt32(), "prizeType", in.readInt32(),
                    "itemId", in.readInt32(), "itemCount", in.readInt32()));
        }
        result.put("prizeInfos", prizes);
        result.put("message", readString(in, 256));
        int itemCount = in.readUInt16();
        result.put("getItemCount", itemCount);
        List<Map<String, Integer>> items = new ArrayList<>();
        for (int i = 0; i < Math.min(itemCount, 5); i++) {
            items.add(Map.of("itemId", in.readInt32(), "itemCount", in.readInt32()));
        }
        result.put("getItems", items);
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeMatchList(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", in.readInt32());
        int resultLength = in.readUInt16();
        result.put("resultStrLen", resultLength);
        result.put("resultStr", new String(in.readBytes(resultLength), StandardCharsets.UTF_8));
        result.put("matchListLen", in.readUInt16());
        appendRemaining(result, in);
        return result;
    }

    /**
     * CRspRecommandRoom::unpack; server/room identifiers are signed i16 on the wire.
     */
    private static Map<String, Object> decodeRecommendRoom(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        int transparentLength = in.readUInt8();
        result.put("transplatomLength", transparentLength);
        result.put("transplatomBuffer", Base64.getEncoder().encodeToString(in.readBytes(transparentLength)));

        Map<String, Object> search = new LinkedHashMap<>();
        search.put("clientVersion", in.readInt32());
        search.put("clientType", in.readInt32());
        search.put("appId", in.readInt32());
        search.put("gameId", in.readInt16());
        search.put("miniOnlinePercent", in.readInt8());
        search.put("maxOnlinePercent", in.readInt8());
        int searchType = in.readInt32();
        search.put("searchType", searchType);
        int conditionLength = in.readUInt8();
        search.put("searchConditionLen", conditionLength);
        byte[] conditionBytes = in.readBytes(conditionLength);
        if (conditionLength == 12 && (searchType == 1 || searchType == 2 || searchType == 3)) {
            TdrBufferReader condition = new TdrBufferReader(conditionBytes);
            search.put("searchTypeDefinedCondition", Map.of("searchCondition", Map.of(
                    "sceneId", condition.readInt32(), "expectAppId", condition.readInt32(),
                    "svrId", condition.readInt16(), "roomId", condition.readInt16())));
        } else {
            search.put("searchConditionBase64", Base64.getEncoder().encodeToString(conditionBytes));
        }
        result.put("searchInfo", search);

        Map<String, Object> found = new LinkedHashMap<>();
        found.put("resultId", in.readInt32());
        int resultStringLength = in.readUInt16();
        found.put("resultStringLen", resultStringLength);
        found.put("resultString", new String(in.readBytes(resultStringLength), StandardCharsets.UTF_8));
        found.put("sceneId", in.readInt32());
        found.put("appId", in.readInt32());
        found.put("svrId", in.readInt16());
        found.put("roomId", in.readInt16());
        found.put("online", in.readInt16());
        result.put("searchResultInfo", found);

        int extensionLength = in.readUInt16();
        result.put("extBufLen", extensionLength);
        result.put("extBuf", Base64.getEncoder().encodeToString(in.readBytes(extensionLength)));
        result.put("isLocked", in.readUInt8());
        result.put("lockType", in.readInt16());
        result.put("lockSvrId", in.readInt16());
        result.put("lockRoomId", in.readInt16());
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeAppDirStuff(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        int identifierLength = in.readUInt16();
        result.put("identifierBase64", Base64.getEncoder().encodeToString(in.readBytes(identifierLength)));
        result.put("resultId", in.readInt16());
        result.put("responseTime", in.readInt32());
        int tlvCount = in.readUInt16();
        result.put("tlvCount", tlvCount);
        byte[] content = new byte[0];
        int stuffId = 0;
        for (int index = 0; index < tlvCount; index++) {
            int type = in.readInt32();
            int length = in.readInt32();
            byte[] value = in.readBytes(length);
            if (type == 10002) {
                DirectoryStuff decoded = decodeDirectoryStuff(value);
                stuffId = decoded.stuffId();
                content = decoded.content();
            }
        }
        result.put("stuffId", stuffId);
        result.put("contentBase64", Base64.getEncoder().encodeToString(content));
        if (in.remaining() >= 2) result.put("responseType", in.readUInt16());
        if (in.remaining() >= 4) result.put("responseFlag", in.readInt32());
        appendRemaining(result, in);
        return result;
    }

    private static DirectoryStuff decodeDirectoryStuff(byte[] value) {
        TdrBufferReader in = new TdrBufferReader(value);
        int stuffId = in.readUInt16();
        in.readUInt16(); // version
        in.readInt32();
        int dimensionCount = in.readUInt16();
        in.readBytes(Math.multiplyExact(dimensionCount, 4));
        in.readBytes(16); // stamp digest
        in.readUInt16();
        int compressed = in.readUInt16();
        int inputLength = in.readInt32();
        int outputLength = in.readInt32();
        byte[] data = in.readBytes(inputLength);
        if (compressed == 1) data = inflate(data, outputLength);
        return new DirectoryStuff(stuffId, data);
    }

    /**
     * Native CResponseLoginHGPara::pack layout recovered from libTSDK.
     */
    private static Map<String, Object> decodeGameLogin(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = decodeGameResultPrefix(in);
        result.put("RecommendSceneID", in.readUInt32());
        result.put("RoomID", in.readInt16());
        result.put("RoomTableNum", in.readInt32());
        result.put("RoomPlayerNum", in.readInt32());
        result.put("RoomTicket", in.readInt32());
        result.put("PlayerID", in.readUInt32());
        result.put("TriggerPresentBeans", in.readUInt32());
        result.putAll(decodeTlvList(in));
        result.put("ControlFlag", in.readUInt32());
        result.put("TalkSwitch", in.readUInt32());
        result.put("OlympicMatchType", in.readInt16());
        result.put("OlympicSeriesTimeType", in.readUInt32());
        result.put("SceneID", in.readUInt32());
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeGameSitDown(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = decodeGameResultPrefix(in);
        result.put("RoomID", in.readInt16());
        result.put("TableID", in.readInt16());
        result.put("SeatID", in.readInt16());
        result.put("TableSeatNum", in.readInt16());
        result.put("CurPlayerNum", in.readInt16());
        result.put("CurViewerNum", in.readInt16());
        result.putAll(decodeTlvList(in));
        appendRemaining(result, in);
        return result;
    }

    /**
     * wxpkg oja: common result prefix followed by one i16 StandUpReason.
     */
    private static Map<String, Object> decodeGameStandUp(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = decodeGameResultPrefix(in);
        result.put("StandUpReason", in.readInt16());
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeGameReady(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = decodeGameResultPrefix(in);
        result.putAll(decodeTlvList(in));
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeGamePlay(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = decodeGameResultPrefix(in);
        appendRemaining(result, in);
        return result;
    }

    /**
     * Yra response paired with Xra/message 15502.
     */
    private static Map<String, Object> decodeCgiResponse(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("Uin", in.readUInt32());
        result.put("ResultID", in.readUInt16());
        int contentLength = in.readUInt16();
        if (contentLength > 20480 || contentLength > in.remaining())
            throw new ProtocolException("invalid CGI response content length " + contentLength);
        byte[] content = in.readBytes(contentLength);
        result.put("ResponseContentLen", contentLength);
        result.put("ResponseContent", Base64.getEncoder().encodeToString(content));
        if (in.remaining() >= 2) result.put("TotalPkgNum", in.readUInt16());
        if (in.remaining() >= 2) result.put("CurPkgNo", in.readUInt16());
        if (in.remaining() >= 4) result.put("TransparentID", in.readInt32());
        if (in.remaining() >= 4) result.put("CgiID", in.readInt32());
        if (in.remaining() >= 1) result.put("RspParaZipFlag", in.readUInt8());
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeGameSvrInfo(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ResultID", in.readUInt32());
        result.put("InfoType", in.readUInt16());
        result.putAll(decodeTlvList(in));
        appendRemaining(result, in);
        return result;
    }

    private static Map<String, Object> decodeMissionStatuses(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ResultID", in.readUInt16());
        result.put("Uin", in.readUInt32());
        result.put("Transparent", in.readInt32());
        result.put("GameID", in.readInt16());
        result.put("SceneID", in.readUInt32());
        result.put("MissionClientType", in.readInt32());
        int count = in.readUInt16();
        if (count > 400) throw new ProtocolException("mission status count exceeds 400: " + count);
        List<Map<String, Object>> statuses = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int gameId = in.readInt16(), missionId = in.readInt16(), status = in.readInt16(), values = in.readUInt16();
            if (values > 4 || in.remaining() < values * Long.BYTES)
                throw new ProtocolException("invalid mission counter count " + values);
            List<Long> counters = new ArrayList<>(values);
            for (int n = 0; n < values; n++) counters.add(in.readInt64());
            statuses.add(Map.of("GameID", gameId, "MissionID", missionId, "Status", status, "CountValue", counters));
        }
        result.put("MissionNum", count);
        result.put("MissionStatus", statuses);
        appendRemaining(result, in);
        return result;
    }

    /**
     * Fixed-width PBPackTo layout used by the JS TDR runtime (Lab), not protobuf wire tags.
     */
    private static byte[] encodeMissionStatus(JsonNode root) {
        JsonNode values = field(root, "missionId", "mission_id");
        int declared = integer(root, "missionIdNum", "mission_id_num");
        int count = values != null && values.isArray() ? Math.min(Math.min(declared, values.size()), 400) : 0;
        TdrBufferWriter out = new TdrBufferWriter()
                .writeInt32(unsignedInt32(root, "uin"))
                .writeInt32(integer(root, "transparent"))
                .writeInt16(integer(root, "gameId", "game_id"))
                .writeInt32(unsignedInt32(root, "sceneId", "scene_id"))
                .writeInt32(integer(root, "missionClientType", "mission_client_type"))
                .writeInt32(unsignedInt32(root, "flag"))
                .writeInt16(integer(root, "beginMissionId", "begin_mission_id"))
                .writeInt16(integer(root, "endMissionId", "end_mission_id"))
                .writeInt16(integer(root, "extFlag", "ext_flag"))
                .writeInt16(count);
        for (int index = 0; index < count; index++) out.writeInt16(values.get(index).asInt());
        out.writeInt32(integer(root, "groupId", "group_id"));
        out.writeInt16(0); // MissionRules.rule_count
        return out.toByteArray();
    }

    private static Map<String, Object> decodeMissionStatusesIfTdr(byte[] payload) {
        try {
            Map<String, Object> result = decodeMissionStatuses(payload);
            long sceneId = ((Number) result.getOrDefault("SceneID", -1)).longValue();
            int clientType = ((Number) result.getOrDefault("MissionClientType", -1)).intValue();
            if (sceneId < 0 || sceneId > 1_000_000 || clientType < 0 || clientType > 4_096
                    || result.containsKey("remainingBase64")) return null;
            return result;
        } catch (RuntimeException notTdr) {
            return null;
        }
    }

    /**
     * Qqa sends a PB request under protocol 23, but the current GameSvr answers with the native
     * Xja TDR object. Keep descriptor PB as a fallback for deployments that mirror the request.
     */
    private static Map<String, Object> decodeGameSvrInfoIfTdr(byte[] payload) {
        try {
            Map<String, Object> result = decodeGameSvrInfo(payload);
            int infoType = ((Number) result.getOrDefault("InfoType", -1)).intValue();
            if (infoType < 0 || infoType > 64 || result.containsKey("remainingBase64")) return null;
            return result;
        } catch (RuntimeException notTdr) {
            return null;
        }
    }

    private static Map<String, Object> decodeGameResultPrefix(TdrBufferReader in) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ResultID", in.readUInt32());
        int length = in.readUInt16();
        result.put("ResultStrLen", length);
        result.put("ResultStr", decodeResultText(in.readBytes(length)));
        return result;
    }

    private static String decodeResultText(byte[] value) {
        String utf8 = new String(value, StandardCharsets.UTF_8);
        return utf8.indexOf('\ufffd') >= 0
                ? new String(value, Charset.forName("GB18030")) : utf8;
    }

    private static Map<String, Object> decodeTlvList(TdrBufferReader in) {
        Map<String, Object> result = new LinkedHashMap<>();
        int count = in.readUInt16();
        if (count > 32) throw new ProtocolException("game TLV count exceeds 32: " + count);
        result.put("TLVNum", count);
        List<Map<String, Object>> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            int type = in.readInt16();
            int length = in.readUInt16();
            values.add(Map.of("Type", type, "Len", length,
                    "Value", Base64.getEncoder().encodeToString(in.readBytes(length))));
        }
        result.put("TLVInfo", values);
        return result;
    }

    private static byte[] inflate(byte[] source, int expectedLength) {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(source);
        byte[] output = new byte[Math.max(expectedLength, 1)];
        try {
            int count = inflater.inflate(output);
            if (!inflater.finished() || count != expectedLength) {
                throw new ProtocolException("AppDir zlib size mismatch: " + count + "/" + expectedLength);
            }
            return java.util.Arrays.copyOf(output, count);
        } catch (java.util.zip.DataFormatException failure) {
            throw new ProtocolException("invalid AppDir zlib payload", failure);
        } finally {
            inflater.end();
        }
    }

    private record DirectoryStuff(int stuffId, byte[] content) {
    }

    private static Map<String, Object> decodeTreasure(byte[] payload) {
        TdrBufferReader in = new TdrBufferReader(payload);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resultId", in.readInt32());
        int rewardCount = in.readUInt16();
        result.put("rewardCount", rewardCount);
        List<Map<String, Integer>> rewards = new ArrayList<>();
        for (int i = 0; i < Math.min(rewardCount, 8); i++) {
            rewards.add(Map.of("itemId", in.readInt32(), "itemCount", in.readInt32()));
        }
        result.put("rewards", rewards);
        int descLength = in.readUInt16();
        result.put("description", new String(in.readBytes(descLength), StandardCharsets.UTF_8));
        appendRemaining(result, in);
        return result;
    }

    private static void writeString(TdrBufferWriter out, String value, int max) {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        if (encoded.length + 1 > max)
            throw new ProtocolException("TDR string exceeds " + max + " bytes");
        out.writeInt32(encoded.length + 1).writeBytes(encoded).writeInt8(0);
    }

    private static String readString(TdrBufferReader in, int max) {
        long length = in.readUInt32();
        if (length < 1 || length > max)
            throw new ProtocolException("invalid TDR string length: " + length);
        byte[] value = in.readBytes((int) length);
        int end = value.length > 0 && value[value.length - 1] == 0 ? value.length - 1 : value.length;
        return new String(value, 0, end, StandardCharsets.UTF_8);
    }

    private static void writeReferencedBytes(TdrBufferWriter out, int length, byte[] value, int max) {
        if (length < 0 || length > max || length != value.length) {
            throw new ProtocolException("TDR referenced byte length mismatch: field=" + length + ", actual=" + value.length);
        }
        out.writeInt16(length).writeBytes(value);
    }

    private static int integerOrLength(JsonNode root, byte[] value, String... names) {
        JsonNode node = field(root, names);
        return node == null ? value.length : node.asInt();
    }

    private static int integer(JsonNode root, String... names) {
        JsonNode node = field(root, names);
        return node == null ? 0 : node.asInt();
    }

    private static long longInteger(JsonNode root, String... names) {
        JsonNode node = field(root, names);
        return node == null ? 0 : node.asLong();
    }

    /**
     * Preserve the low 32 wire bits for JSON values in the unsigned u32 range.
     */
    private static int unsignedInt32(JsonNode root, String... names) {
        long value = longInteger(root, names);
        if (value < 0 || value > 0xffff_ffffL) {
            throw new ProtocolException("TDR u32 outside range: " + value);
        }
        return (int) value;
    }

    private static String text(JsonNode root, String... names) {
        JsonNode node = field(root, names);
        return node == null ? "" : node.asString("");
    }

    private static byte[] bytes(JsonNode root, String... names) throws IOException {
        JsonNode node = field(root, names);
        if (node == null || node.isNull()) return new byte[0];
        if (node.isBinary()) return node.binaryValue();
        if (node.isArray()) {
            byte[] value = new byte[node.size()];
            for (int i = 0; i < value.length; i++) value[i] = (byte) node.get(i).asInt();
            return value;
        }
        try {
            return Base64.getDecoder().decode(node.asString());
        } catch (IllegalArgumentException ignored) {
            return node.asString().getBytes(StandardCharsets.UTF_8);
        }
    }

    private static JsonNode field(JsonNode root, String... names) {
        if (root == null) return null;
        for (String name : names) {
            JsonNode value = root.get(name);
            if (value != null && !value.isNull()) return value;
        }
        return null;
    }

    private static void appendRemaining(Map<String, Object> result, TdrBufferReader in) {
        if (in.remaining() > 0) result.put("remainingBase64",
                Base64.getEncoder().encodeToString(in.readBytes(in.remaining())));
    }

    private String raw(String schema, byte[] payload, String decodeError) {
        try {
            return json.writeValueAsString(Map.of("schema", schema, "decodeError",
                    decodeError == null ? "" : decodeError,
                    "payloadBase64", Base64.getEncoder().encodeToString(payload)));
        } catch (RuntimeException impossible) {
            return "{\"payloadBase64\":\"" + Base64.getEncoder().encodeToString(payload) + "\"}";
        }
    }
}
