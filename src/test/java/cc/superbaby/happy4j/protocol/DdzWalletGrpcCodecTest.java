package cc.superbaby.happy4j.protocol;

import cc.superbaby.happy4j.application.port.GameTransportPort.GrpcResponse;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.PlatformCredential;
import cc.superbaby.happy4j.protocol.generated.ddzquery.CReqQueryInfoByBitflag;
import cc.superbaby.happy4j.protocol.grpc.DdzWalletGrpcCodec;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DdzWalletGrpcCodecTest {
    private final DdzWalletGrpcCodec codec = new DdzWalletGrpcCodec();

    @Test
    void requestCarriesTheCurrentQueryInfoContract() throws Exception {
        GameCredential credential = credential();
        Happy4jProperties.Game game = new Happy4jProperties.Game(260, 64, 1, 137887760,
                10040714, "35", "legacy", "qq", "wx", "lotus", 443,
                Duration.ofSeconds(5), Duration.ofSeconds(8));

        var request = codec.query(credential, game);
        CReqQueryInfoByBitflag body = CReqQueryInfoByBitflag.parseFrom(request.body());

        assertThat(request.fullMethodName()).isEqualTo(DdzWalletGrpcCodec.METHOD);
        assertThat(body.getTargetUin()).isEqualTo(397911882);
        assertThat(body.getTargetAccountType()).isEqualTo(1);
        assertThat(body.getTargetClientType()).isEqualTo(64);
        assertThat(body.getQueryFlag()).isEqualTo(3);
        assertThat(body.getClientAbility()).isEqualTo(8);
        assertThat(body.getClientVersion()).isEqualTo(137887760);
        assertThat(body.getMidasInfo().getPf()).isEqualTo("pf");
    }

    @Test
    void responseFixtureDecodesServerAuthoritativeBeanAmount() {
        byte[] body = HexFormat.of().parseHex(
                "080312490a4710f85518dffffaffffffffffff0120e3af0128fd97feffffffffffff01"
                        + "30a78dbed40638db3440e7eaf9830648e70a5882bde4d1066095df05700278d00f"
                        + "80019423b001d8ce04");

        var wallet = codec.decode(new GrpcResponse(0, DdzWalletGrpcCodec.METHOD, 0, "", body));

        assertThat(wallet.beans()).isEqualTo(11_000);
        assertThat(wallet.diamonds()).isZero();
    }

    private static GameCredential credential() {
        return new GameCredential(397911882, 0, PlatformCredential.Provider.WECHAT,
                new byte[16], new byte[]{1}, "0123456789abcdef", "openid", "openkey",
                "pay", "pf", "pfkey", 0, 0, Map.of());
    }
}
