package cc.superbaby.happy4j.protocol;

import cc.superbaby.happy4j.protocol.schema.LotusPayloadCodec;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.util.HexFormat;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class LotusPayloadCodecTest {
    private final LotusPayloadCodec codec = new LotusPayloadCodec(
            ProtocolRegistry.loadDefault(), new JsonMapper());

    @Test
    void signInUsesRecoveredFixedWidthTdrInsteadOfProtobufTags() {
        byte[] value = codec.encodeJson("Tdrpb.HLDDZSignInLottery.SignInLotteryRequest", """
                {"uin":16909060,"accountType":1,"clientType":64,"openid":"wx",
                 "command":1,"choosePrizeIndex":0,"abilityFlags":1,"loginPlatform":0}
                """);

        assertThat(HexFormat.of().formatHex(value)).isEqualTo(
                "010203040000000100000040000000037778000001000000000001000000000000000000000000");
    }

    @Test
    void unsignedGameUinAboveIntegerMaxKeepsItsFourWireBytes() {
        byte[] value = codec.encodeJson("Tdrpb.HLDDZSignInLottery.SignInLotteryRequest", """
                {"uin":2234753103,"accountType":2,"clientType":64,"openid":"qq","command":1}
                """);
        assertThat(HexFormat.of().formatHex(value)).startsWith("8533a04f0000000200000040");
    }

    @Test
    void beanQueryUsesLengthPrefixedCStringAndOneByteTuringFlag() {
        byte[] value = codec.encodeJson("Tdrpb.HLDDZBuyBeanDisCount.CReqBuyBeanDisCountQuery", """
                {"uin":1,"gameId":260,"accountType":1,"clientType":64,
                 "openId":"a","openKey":"b","needUpdateTuringInfo":1}
                """);

        assertThat(HexFormat.of().formatHex(value)).isEqualTo(
                "0000000100000104000000010000004000000002610000000002620001");
    }

    @Test
    void itemInventoryUsesCurrentWxpkg15903TdrLayout() {
        byte[] request = codec.encodeJson(LotusPayloadCodec.ITEM_QUERY_REQ, """
                {"uin":16909060,"appId":260,"clientType":64,"accountType":1,
                 "itemIds":[30020001],"requestFlags":4}
                """);
        assertThat(HexFormat.of().formatHex(request)).isEqualTo(
                "000001020304000001040000004000000001000101ca11a1000000040000");

        byte[] response = new cc.superbaby.happy4j.protocol.wire.TdrBufferWriter()
                .writeInt16(0).writeInt32(0).writeInt32(0x01020304).writeInt16(1)
                .writeInt32(9).writeInt32(30020001).writeInt32(3)
                .writeInt32(1).writeInt32(2).writeInt32(3)
                .writeInt16(2).writeBytes(HexFormat.of().parseHex("aabb")).writeInt32(4)
                .toByteArray();
        assertThat(codec.decodeJson(LotusPayloadCodec.ITEM_QUERY_RSP, response))
                .contains("\"resultId\":0", "\"itemId\":30020001", "\"count\":3", "\"serverTime\":4");
    }

    @Test
    void gameLoginUsesRecoveredTdrClientInfoLayout() {
        byte[] value = codec.encodeJson("Native.HuanLeGameSvr.CRequestLoginHGPara", """
                {"Uin":16909060,"RequestSrc":2,"LoginLifeStyle":3,"RoomID":4,
                 "ClientInfo":{"NickLen":2,"Nick":"YWI=","Age":5,"Gender":6,
                   "AccountType":1,"ClientType":64,"PFKey":72623859790382856,
                   "Network":1,"Operator":2,"Channel":10040714,"ClientVersion":137887760,
                   "OSVersionlLen":2,"OSVersion":"MzU=","TerminalLen":1,"Terminal":"eA==",
                   "OpenIDLen":2,"OpenID":"d3g=","HeadUrlLen":0},"TLVNum":0}
                """);

        assertThat(HexFormat.of().formatHex(value)).isEqualTo(
                "0102030400020000000300000004000261620506000100400102030405060708"
                        + "000100020099358a08380010000233350001780002777800000000");
    }

    @Test
    void gameSitReadyAndLoginResponseUseSignedShortRoomFields() {
        assertThat(HexFormat.of().formatHex(codec.encodeJson(
                "Native.HuanLeGameSvr.CRequestSitDownHGPara",
                "{\"SitDownFlag\":1,\"RoomID\":2,\"TableID\":3,\"SeatID\":-1,\"TLVNum\":0}")))
                .isEqualTo("0000000100020003ffff0000");
        assertThat(HexFormat.of().formatHex(codec.encodeJson(
                "Native.HuanLeGameSvr.CRequestReadyHGPara",
                "{\"ReadyMode\":1,\"RoomID\":2,\"TableID\":3,\"TLVNum\":0}")))
                .isEqualTo("0001000200030000");

        String decoded = codec.decodeJson("Native.HuanLeGameSvr.CResponseLoginHGPara", HexFormat.of().parseHex(
                "0000000000026f6b000004ec00000000000a000000140000001e0000002a00000064"
                        + "00000000000100000002000300000004000004ec"));
        assertThat(decoded).contains("\"ResultID\":0", "\"RoomID\":0", "\"PlayerID\":42",
                "\"SceneID\":1260");
    }

    @Test
    void gameStandUpUsesNativeTdrRequestAndResponse() {
        assertThat(HexFormat.of().formatHex(codec.encodeJson(
                "Native.HuanLeGameSvr.CRequestStandUpHGPara",
                "{\"StandUpFlag\":1,\"RoomID\":2}")))
                .isEqualTo("000000010002");

        assertThat(codec.decodeJson("Native.HuanLeGameSvr.CResponseStandUpHGPara",
                HexFormat.of().parseHex("0000000000000003")))
                .contains("\"ResultID\":0", "\"ResultStr\":\"\"", "\"StandUpReason\":3");
    }

    @Test
    void gameSitCanCarryRecoveredUnityClientMetadataTlvs() {
        byte[] value = codec.encodeJson("Native.HuanLeGameSvr.CRequestSitDownHGPara", """
                {"SitDownFlag":3,"RoomID":2,"TableID":3,"SeatID":-1,"TLVNum":2,
                 "TLVInfo":[
                   {"Type":20012,"Len":10,"Value":"igUHCAMSA5gBAg=="},
                   {"Type":20017,"Len":14,"Value":"ygULCOH8/78BEJqFzmA="}
                 ]}
                """);

        assertThat(HexFormat.of().formatHex(value)).isEqualTo(
                "0000000300020003ffff00024e2c000a8a050708031203980102"
                        + "4e31000eca050b08e1fcffbf01109a85ce60");
    }

    @Test
    void miniGameQuickMatchSitMatchesWxpkgWithoutUnityTlvs() {
        byte[] value = codec.encodeJson("Native.HuanLeGameSvr.CRequestSitDownHGPara", """
                {"SitDownFlag":3,"RoomID":0,"TableID":-1,"SeatID":-1,
                 "TLVNum":0,"TLVInfo":[]}
                """);

        assertThat(HexFormat.of().formatHex(value))
                .isEqualTo("000000030000ffffffff0000");
    }

    @Test
    void gamePlayWrapsActionBytesInNativeTdr() {
        byte[] value = codec.encodeJson("Native.HuanLeGameSvr.CRequestPlayHGPara",
                "{\"Uin\":16909060,\"Time\":5,\"PlayerDataLength\":3,\"PlayData\":\"AQID\"}");
        assertThat(HexFormat.of().formatHex(value)).isEqualTo("01020304000000050003010203");
        assertThat(codec.decodeJson("Native.HuanLeGameSvr.CResponsePlayHGPara",
                HexFormat.of().parseHex("0000000000026f6b")))
                .contains("\"ResultID\":0", "\"ResultStr\":\"ok\"");
    }

    @Test
    void gameSvrInfoTypeTwoUsesNativeReplayQueryLayout() {
        assertThat(HexFormat.of().formatHex(codec.encodeJson(
                "Native.HuanLeGameSvr.CRequestSvrInfoHGPara", "{\"InfoType\":2}")))
                .isEqualTo("0802");
        ProtocolRegistry registry = ProtocolRegistry.loadDefault();
        byte[] response = registry.encodeJson("Native.HuanLeGameSvr.CResponseSvrInfoHGPara", """
                {"ResultID":0,"InfoType":2,"TLVNum":1,
                 "TLVInfo":[{"Type":10014,"Len":4,"Value":"3q2+7w=="}]}
                """);
        String decoded = codec.decodeJson("Native.HuanLeGameSvr.CResponseSvrInfoHGPara",
                response);
        assertThat(decoded).contains("\"ResultID\": 0", "\"InfoType\": 2", "\"Type\": 10014",
                "3q2+7w==");

        byte[] tdrResponse = new cc.superbaby.happy4j.protocol.wire.TdrBufferWriter()
                .writeInt32(0).writeInt16(2).writeInt16(1)
                .writeInt16(10014).writeInt16(4).writeBytes(HexFormat.of().parseHex("deadbeef"))
                .toByteArray();
        assertThat(codec.decodeJson("Native.HuanLeGameSvr.CResponseSvrInfoHGPara", tdrResponse))
                .contains("\"ResultID\":0", "\"InfoType\":2", "\"Type\":10014", "3q2+7w==");
    }

    @Test
    void cgiTunnelUsesWxpkgXraAndYraNativeLayouts() {
        byte[] request = codec.encodeJson("Tdrpb.HttpOutSvr.CSimpleHttpRequestMsgPara", """
                {"Uin":16909060,"IsNoNeedRsp":0,"IsHTTPS":0,"URL":"",
                 "CGIPostPara":"e30=","Cookie":"YT1i","TransparentID":0,
                 "CgiID":32791,"CGIGetPara":"Y21kPWdldA==","ReqParaZipFlag":0,
                 "RspParaZipFlag":0,"GameID":0,"SrcClientType":3}
                """);
        cc.superbaby.happy4j.protocol.wire.TdrBufferReader in =
                new cc.superbaby.happy4j.protocol.wire.TdrBufferReader(request);
        assertThat(in.readUInt32()).isEqualTo(0x01020304L);
        assertThat(in.readUInt8()).isZero();
        assertThat(in.readUInt8()).isZero();
        assertThat(in.readUInt16()).isZero();
        assertThat(new String(in.readBytes(in.readUInt16()))).isEqualTo("{}");
        assertThat(new String(in.readBytes(in.readUInt16()))).isEqualTo("a=b");
        assertThat(in.readInt32()).isZero();
        assertThat(in.readInt32()).isEqualTo(32791);
        assertThat(new String(in.readBytes(in.readUInt16()))).isEqualTo("cmd=get");
        assertThat(in.readUInt8()).isZero();
        assertThat(in.readUInt8()).isZero();
        assertThat(in.readUInt16()).isZero();
        assertThat(in.readInt32()).isZero();
        assertThat(in.readUInt32()).isEqualTo(3);
        assertThat(in.readUInt16()).isZero();
        assertThat(in.remaining()).isZero();

        byte[] content = "{\"CgiRet\":0}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        cc.superbaby.happy4j.protocol.wire.TdrBufferWriter response =
                new cc.superbaby.happy4j.protocol.wire.TdrBufferWriter()
                        .writeInt32(0x01020304).writeInt16(0).writeInt16(content.length)
                        .writeBytes(content).writeInt16(1).writeInt16(0)
                        .writeInt32(0).writeInt32(32791).writeInt8(0);
        String decoded = codec.decodeJson("Tdrpb.HttpOutSvr.CSimpleHttpResponseMsgPara",
                response.toByteArray());
        assertThat(decoded).contains("\"CgiID\":32791", Base64.getEncoder().encodeToString(content));
    }

    @Test
    void decodesOnlineMissionStatusResponseFromMissionServer() {
        byte[] response = new cc.superbaby.happy4j.protocol.wire.TdrBufferWriter()
                .writeInt16(0).writeInt32(397911882).writeInt32(0).writeInt16(260)
                .writeInt32(0).writeInt32(64).writeInt16(2)
                .writeInt16(260).writeInt16(1563).writeInt16(2).writeInt16(1).writeInt64(3)
                .writeInt16(260).writeInt16(2662).writeInt16(1).writeInt16(1).writeInt64(8)
                .toByteArray();
        String decoded = codec.decodeJson("Tdrpb.MissionSvrMsgPara.CRspFetchMissionStatus", response);
        assertThat(decoded).contains("\"MissionNum\":2", "\"MissionID\":1563", "\"Status\":2", "\"MissionID\":2662");
    }

    @Test
    void fallsBackToProtobufForMissionStatusResponseUsedByCurrentLotus() {
        String schema = "Tdrpb.MissionSvrMsgPara.CRspFetchMissionStatus";
        byte[] response = ProtocolRegistry.loadDefault().encodeJson(schema, """
                {"uin":397911882,"gameId":260,"missionClientType":64,"missionNum":1,
                 "missionStatus":[{"gameId":260,"missionId":1563,"missionStatus":2,
                   "countValueNum":1,"countValue":[3]}]}
                """);

        assertThat(codec.decodeJson(schema, response))
                .contains("\"uin\": 397911882", "\"missionId\": 1563", "\"missionStatus\": 2");
    }
}
