package cc.superbaby.happy4j.infrastructure.transport;

import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolRequest;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.PlatformCredential;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;
import cc.superbaby.happy4j.protocol.wire.CMsgFrame;
import cc.superbaby.happy4j.protocol.wire.CcsFrameCodec;
import cc.superbaby.happy4j.protocol.wire.CcsHeader;
import cc.superbaby.happy4j.protocol.wire.FunctionalEntity;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SocketLotusTransportTest {
    @Test
    void firstAuthenticatedFrameCarriesStAndUsesEnKey() throws Exception {
        byte[] enKey = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] st = {1, 2, 3, 4};
        try (ServerSocket listener = new ServerSocket(0)) {
            CompletableFuture<byte[]> received = CompletableFuture.supplyAsync(() -> {
                try (Socket peer = listener.accept(); InputStream input = peer.getInputStream()) {
                    byte[] length = input.readNBytes(2);
                    int count = ((length[0] & 0xff) << 8) | (length[1] & 0xff);
                    byte[] rest = input.readNBytes(count - 2);
                    byte[] packet = new byte[count];
                    System.arraycopy(length, 0, packet, 0, 2);
                    System.arraycopy(rest, 0, packet, 2, rest.length);
                    return packet;
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });

            Happy4jProperties.Game config = new Happy4jProperties.Game(260, 64, 1,
                    137887760, 10040714, "35", "legacy", "qq", "wx", "127.0.0.1",
                    listener.getLocalPort(), Duration.ofSeconds(2), Duration.ofSeconds(2));
            SocketLotusTransport transport = new SocketLotusTransport(config,
                    ProtocolRegistry.loadDefault(), new JsonMapper());
            GameCredential credential = new GameCredential(0x12345678L, 1,
                    PlatformCredential.Provider.WECHAT, new byte[16], st,
                    new String(enKey, StandardCharsets.ISO_8859_1), "wx", "token", "", "", "",
                    0, 0, Map.of());
            transport.connect(credential, ignored -> {
            });
            transport.send(new ProtocolRequest(18291, FunctionalEntity.LOGIC_SERVICE_SERVER,
                    (short) -1, "Tdrpb.HLDDZSignInLottery.SignInLotteryRequest",
                    "Tdrpb.HLDDZSignInLottery.SignInLotteryResponse",
                    "{\"uin\":305419896,\"accountType\":1,\"clientType\":64,\"openid\":\"wx\",\"command\":1}", true));

            CMsgFrame frame = new CcsFrameCodec().decode(received.get(3, TimeUnit.SECONDS), enKey);
            assertThat(frame.transportHeader().protocolVersion()).isZero();
            assertThat(frame.transportHeader().sequence()).isEqualTo(-1);
            assertThat(frame.transportHeader().platform()).isEqualTo((short) -3);
            assertThat(frame.transportHeader().flags()).isEqualTo(CcsHeader.LOTUS_AUTHENTICATED_FLAGS);
            assertThat(frame.transportHeader().sessionTicket()).containsExactly(st);
            assertThat(frame.messageHeader().messageId()).isEqualTo(18291);
            assertThat(frame.messageHeader().destinationEntity()).isEqualTo(FunctionalEntity.LOGIC_SERVICE_SERVER);
            assertThat(frame.payload()).startsWith(0x12, 0x34, 0x56, 0x78);
            transport.close();
        }
    }
}
