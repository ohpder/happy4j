package cc.superbaby.happy4j.protocol.grpc;

import cc.superbaby.happy4j.application.port.GameTransportPort.GrpcRequest;
import cc.superbaby.happy4j.application.port.GameTransportPort.GrpcResponse;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.WalletBalance;
import cc.superbaby.happy4j.protocol.generated.ddzquery.CReqQueryInfoByBitflag;
import cc.superbaby.happy4j.protocol.generated.ddzquery.CRspQueryInfoByBitflag;
import cc.superbaby.happy4j.protocol.generated.ddzquery.QueryRspData;
import cc.superbaby.happy4j.protocol.generated.nativehlbase.MidasInfo;

import com.google.protobuf.InvalidProtocolBufferException;

import java.time.Instant;

/**
 * Exact protobuf body codec for the current DDZ3D wallet gRPC method.
 */
public final class DdzWalletGrpcCodec {
    public static final String METHOD =
            "/DDZ3DQueryInfo.DDZ3DQueryInfoService/QueryInfoByBitflag";
    static final long QUERY_BEANS_AND_DIAMONDS = 3;
    static final long CLIENT_ABILITY = 8;

    public GrpcRequest query(GameCredential credential, Happy4jProperties.Game game) {
        MidasInfo midas = MidasInfo.newBuilder()
                .setPf(value(credential.pf()))
                .setPfkey(value(credential.pfKey()))
                .setPaytoken(value(credential.payToken()))
                .build();
        CReqQueryInfoByBitflag body = CReqQueryInfoByBitflag.newBuilder()
                .setTargetUin((int) credential.uin())
                .setTargetAccountType(credential.accountType())
                .setTargetClientType(game.clientType())
                .setTargetOpenID(value(credential.openId()))
                .setMidasInfo(midas)
                .setQueryFlag(QUERY_BEANS_AND_DIAMONDS)
                .setClientAbility(CLIENT_ABILITY)
                .setClientVersion(game.clientVersion())
                .build();
        return new GrpcRequest(METHOD, "", "", body.toByteArray());
    }

    public WalletBalance decode(GrpcResponse response) {
        if (response.resultCode() != 0) {
            throw new IllegalStateException("QueryInfoByBitflag result=" + response.resultCode()
                    + (response.resultMessage().isBlank() ? "" : ": " + response.resultMessage()));
        }
        try {
            CRspQueryInfoByBitflag body = CRspQueryInfoByBitflag.parseFrom(response.body());
            QueryRspData data = body.hasRspData() ? body.getRspData() : QueryRspData.getDefaultInstance();
            long beans = data.hasQueryBean() ? data.getQueryBean().getBeanAmt() : 0;
            long diamonds = data.hasQueryDiamond() ? Integer.toUnsignedLong(data.getQueryDiamond().getDiamond()) : 0;
            long goldCoins = data.hasQueryGoldCoin() ? data.getQueryGoldCoin().getBeanAmt() : 0;
            return new WalletBalance(beans, diamonds, goldCoins, Instant.now());
        } catch (InvalidProtocolBufferException failure) {
            throw new IllegalStateException("QueryInfoByBitflag protobuf 响应损坏", failure);
        }
    }

    private static String value(String source) {
        return source == null ? "" : source;
    }
}
