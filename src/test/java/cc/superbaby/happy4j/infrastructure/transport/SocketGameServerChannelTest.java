package cc.superbaby.happy4j.infrastructure.transport;

import cc.superbaby.happy4j.application.port.GameTransportPort.GameServerEndpoint;
import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolRequest;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.domain.PlatformCredential;
import cc.superbaby.happy4j.protocol.schema.LotusPayloadCodec;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;
import cc.superbaby.happy4j.protocol.wire.CMsgFrame;
import cc.superbaby.happy4j.protocol.wire.CMsgHeader;
import cc.superbaby.happy4j.protocol.wire.CcsFrameCodec;
import cc.superbaby.happy4j.protocol.wire.CcsHeader;
import cc.superbaby.happy4j.protocol.wire.FunctionalEntity;
import cc.superbaby.happy4j.protocol.wire.MessageType;
import cc.superbaby.happy4j.protocol.wire.TdrBufferReader;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class SocketGameServerChannelTest {
    @Test
    void performsTgwBookLoginAndPromotesPlayerIdForFollowingFrames() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] st = {1, 2, 3, 4};
        CcsFrameCodec frames = new CcsFrameCodec();
        try (ServerSocket listener = new ServerSocket(0)) {
            CompletableFuture<Integer> readyOuterSequence = new CompletableFuture<>();
            Thread fixtureServer = new Thread(() -> {
                try (Socket peer = listener.accept()) {
                    String tgw = readTgw(peer.getInputStream());
                    assertThat(tgw).isEqualTo("tgw_l7_forward\r\nHost: gamesvrid7540.happyapp.huanle.qq.com:443\r\n\r\n");
                    CMsgFrame book = frames.decode(readPacket(peer.getInputStream()), key);
                    assertThat(book.messageHeader().messageId()).isEqualTo(7601);
                    assertThat(book.messageHeader().messageType()).isEqualTo(MessageType.NOTIFY.wireValue());
                    assertThat(book.messageHeader().destinationEntity()).isEqualTo(FunctionalEntity.CLIENT);
                    assertThat(book.messageHeader().sourceId()).isZero();
                    assertThat(book.messageHeader().destinationId()).isZero();
                    assertThat(book.transportHeader().protocolVersion()).isZero();
                    assertThat(HexFormat.of().formatHex(book.payload()))
                            .isEqualTo("12345678000000000001000400001d74");

                    CMsgFrame login = frames.decode(readPacket(peer.getInputStream()), key);
                    assertThat(login.messageHeader().messageId()).isEqualTo(15801);
                    assertThat(login.messageHeader().destinationEntity()).isEqualTo(FunctionalEntity.CLIENT);
                    assertThat(login.messageHeader().destinationId()).isZero();
                    TdrBufferReader loginTdr = new TdrBufferReader(login.payload());
                    loginTdr.readBytes(50);
                    assertThat(loginTdr.readUInt16()).isEqualTo(3);
                    assertThat(loginTdr.readInt16()).isEqualTo(10012);
                    assertThat(loginTdr.readUInt16()).isEqualTo(8);
                    assertThat(loginTdr.readInt64()).isEqualTo(0x3f7b7bb9e2b08e99L);
                    assertThat(loginTdr.readInt16()).isEqualTo(5);
                    int deviceTlvLength = loginTdr.readUInt16();
                    assertThat(loginTdr.readBytes(deviceTlvLength)).startsWith(0, 17);
                    assertThat(loginTdr.readInt16()).isEqualTo(7);
                    int routeTlvLength = loginTdr.readUInt16();
                    assertThat(loginTdr.readBytes(routeTlvLength)).containsSequence(
                            "127.0.0.1".getBytes(StandardCharsets.US_ASCII));
                    writePush(peer, frames, key, st, 15814, login.messageHeader().sequence(),
                            new byte[]{0, 1, 2});
                    byte[] loginBody = HexFormat.of().parseHex(
                            "000000000000000004ec00000000000000000000000000000000002a0000000000000000"
                                    + "00000000000000000000000000000004ec");
                    writeWrappedResponse(peer, frames, key, st, login, loginBody);

                    CMsgFrame replay = frames.decode(readPacket(peer.getInputStream()), key);
                    assertThat(replay.messageHeader().messageId()).isEqualTo(15806);
                    assertThat(replay.transportHeader().protocolVersion()).isEqualTo(
                            CcsHeader.PROTOBUF_PROTOCOL_VERSION);
                    assertThat(replay.messageHeader().sequence()).isZero();
                    assertThat(replay.messageHeader().destinationEntity()).isEqualTo(FunctionalEntity.CLIENT);
                    assertThat(replay.messageHeader().sourceId()).isZero();
                    assertThat(replay.messageHeader().destinationId()).isZero();
                    assertThat(HexFormat.of().formatHex(replay.payload())).isEqualTo("0802");
                    writeResponse(peer, frames, key, st, replay,
                            HexFormat.of().parseHex("1002"));

                    CMsgFrame ready = frames.decode(readPacket(peer.getInputStream()), key);
                    assertThat(ready.transportHeader().protocolVersion()).isEqualTo(
                            CcsHeader.TDR_PROTOCOL_VERSION);
                    assertThat(ready.messageHeader().sequence()).isEqualTo(131415);
                    assertThat(ready.messageHeader().destinationId()).isZero();
                    writeResponse(peer, frames, key, st, ready,
                            HexFormat.of().parseHex("0000000000000000"));
                    writeWrappedBatch(peer, frames, key, st, List.of(
                            new Wrapped(15809, new byte[]{1, 2}),
                            new Wrapped(15815, new byte[]{3, 4, 5})));
                    readyOuterSequence.complete(ready.transportHeader().sequence());
                } catch (Exception failure) {
                    readyOuterSequence.completeExceptionally(failure);
                }
            }, "game-server-fixture");
            fixtureServer.start();

            Happy4jProperties.Game config = new Happy4jProperties.Game(260, 64, 1,
                    137887760, 10040714, "35", "legacy", "qq", "wx", "lotus", 443,
                    Duration.ofSeconds(2), Duration.ofSeconds(2));
            JsonMapper json = new JsonMapper();
            GameCredential credential = new GameCredential(0x12345678L, 0,
                    PlatformCredential.Provider.WECHAT, new byte[16], st,
                    new String(key, StandardCharsets.ISO_8859_1), "wx", "token", "", "", "",
                    0, 0, Map.of());
            CopyOnWriteArrayList<Integer> pushes = new CopyOnWriteArrayList<>();
            SocketGameServerChannel channel = new SocketGameServerChannel(config,
                    new LotusPayloadCodec(ProtocolRegistry.loadDefault(), json), json, credential,
                    event -> pushes.add(event.messageId()));
            channel.connect(List.of(new GameServerEndpoint(7540, "127.0.0.1", listener.getLocalPort())));

            ProtocolRequest login = new ProtocolRequest(15801, FunctionalEntity.SERVER, (short) -1,
                    "Native.HuanLeGameSvr.CRequestLoginHGPara", "Native.HuanLeGameSvr.CResponseLoginHGPara",
                    "{\"Uin\":305419896,\"RoomID\":0,\"ClientInfo\":{\"AccountType\":1,\"ClientType\":64},\"TLVNum\":0}", true);
            assertThat(channel.request(login, Duration.ofSeconds(2)).get(2, TimeUnit.SECONDS).json())
                    .contains("\"PlayerID\":42");
            assertThat(pushes).containsExactly(15814);
            ProtocolRequest replay = new ProtocolRequest(15806, FunctionalEntity.SERVER, (short) -1,
                    "Native.HuanLeGameSvr.CRequestSvrInfoHGPara",
                    "Native.HuanLeGameSvr.CResponseSvrInfoHGPara", "{\"InfoType\":2}", true);
            assertThat(channel.request(replay, Duration.ofSeconds(2)).get(2, TimeUnit.SECONDS).json())
                    .contains("\"InfoType\": 2");
            ProtocolRequest ready = new ProtocolRequest(15810, FunctionalEntity.SERVER, (short) -1,
                    "Native.HuanLeGameSvr.CRequestReadyHGPara", "Native.HuanLeGameSvr.CResponseReadyHGPara",
                    "{\"ReadyMode\":0,\"RoomID\":0,\"TableID\":0,\"TLVNum\":0}", true);
            channel.request(ready, Duration.ofSeconds(2)).get(2, TimeUnit.SECONDS);
            assertThat(readyOuterSequence.get(2, TimeUnit.SECONDS)).isEqualTo(42);
            for (int i = 0; i < 20 && pushes.size() < 3; i++) Thread.sleep(10);
            assertThat(pushes).startsWith(15814, 15809, 15815);
            channel.close();
        }
    }

    private static void writeResponse(Socket peer, CcsFrameCodec frames, byte[] key, byte[] st,
                                      CMsgFrame request, byte[] payload) throws Exception {
        CcsHeader outer = new CcsHeader(0, CcsHeader.TDR_PROTOCOL_VERSION, -1, (short) -3,
                0x12345678L, CcsHeader.LOTUS_AUTHENTICATED_FLAGS, st);
        CMsgHeader inner = CMsgHeader.of(request.messageHeader().messageId(), MessageType.RESPONSE,
                request.messageHeader().sequence(), FunctionalEntity.SERVER, FunctionalEntity.CLIENT,
                (short) -1, (short) -1);
        peer.getOutputStream().write(frames.encode(new CMsgFrame(outer, inner, payload), key));
        peer.getOutputStream().flush();
    }

    private static void writePush(Socket peer, CcsFrameCodec frames, byte[] key, byte[] st,
                                  int messageId, int sequence, byte[] payload) throws Exception {
        CcsHeader outer = new CcsHeader(0, CcsHeader.TDR_PROTOCOL_VERSION, -1, (short) -3,
                0x12345678L, CcsHeader.LOTUS_AUTHENTICATED_FLAGS, st);
        CMsgHeader inner = CMsgHeader.of(messageId, MessageType.NOTIFY, sequence,
                FunctionalEntity.SERVER, FunctionalEntity.CLIENT, (short) -1, (short) -1);
        peer.getOutputStream().write(frames.encode(new CMsgFrame(outer, inner, payload), key));
        peer.getOutputStream().flush();
    }

    private static void writeWrappedResponse(Socket peer, CcsFrameCodec frames, byte[] key, byte[] st,
                                             CMsgFrame request, byte[] payload) throws Exception {
        CMsgHeader embeddedHeader = CMsgHeader.of(request.messageHeader().messageId(), MessageType.RESPONSE,
                request.messageHeader().sequence(), FunctionalEntity.SERVER, FunctionalEntity.CLIENT,
                (short) 0, (short) -1);
        ByteBuffer embedded = ByteBuffer.allocate(CMsgHeader.WIRE_SIZE + 2 + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        embeddedHeader.writeTo(embedded);
        embedded.putShort((short) (payload.length + 2)).put(payload);
        ByteBuffer wrapperPayload = ByteBuffer.allocate(4 + embedded.position()).order(ByteOrder.BIG_ENDIAN);
        wrapperPayload.putShort((short) 1).putShort((short) embedded.position()).put(embedded.array());
        CcsHeader outer = new CcsHeader(0, CcsHeader.TDR_PROTOCOL_VERSION, -1, (short) -3,
                0x12345678L, CcsHeader.LOTUS_AUTHENTICATED_FLAGS, st);
        CMsgHeader wrapper = CMsgHeader.of(15814, MessageType.NOTIFY, request.messageHeader().sequence(),
                FunctionalEntity.SERVER, FunctionalEntity.CLIENT, (short) -1, (short) -1);
        peer.getOutputStream().write(frames.encode(new CMsgFrame(outer, wrapper, wrapperPayload.array()), key));
        peer.getOutputStream().flush();
    }

    private static void writeWrappedBatch(Socket peer, CcsFrameCodec frames, byte[] key, byte[] st,
                                          List<Wrapped> values) throws Exception {
        int size = 2;
        for (Wrapped value : values) size += 2 + CMsgHeader.WIRE_SIZE + 2 + value.payload().length;
        ByteBuffer wrapperPayload = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        wrapperPayload.putShort((short) values.size());
        for (Wrapped value : values) {
            int messageLength = CMsgHeader.WIRE_SIZE + 2 + value.payload().length;
            wrapperPayload.putShort((short) messageLength);
            CMsgHeader.of(value.messageId(), MessageType.NOTIFY, 1,
                            FunctionalEntity.SERVER, FunctionalEntity.CLIENT, (short) -1, (short) -1)
                    .writeTo(wrapperPayload);
            wrapperPayload.putShort((short) (value.payload().length + 2)).put(value.payload());
        }
        CcsHeader outer = new CcsHeader(0, CcsHeader.TDR_PROTOCOL_VERSION, -1, (short) -3,
                0x12345678L, CcsHeader.LOTUS_AUTHENTICATED_FLAGS, st);
        CMsgHeader wrapper = CMsgHeader.of(15814, MessageType.NOTIFY, 1,
                FunctionalEntity.SERVER, FunctionalEntity.CLIENT, (short) -1, (short) -1);
        peer.getOutputStream().write(frames.encode(
                new CMsgFrame(outer, wrapper, wrapperPayload.array()), key));
        peer.getOutputStream().flush();
    }

    private record Wrapped(int messageId, byte[] payload) {
    }

    private static byte[] readPacket(InputStream input) throws Exception {
        byte[] length = input.readNBytes(2);
        int count = (length[0] & 0xff) << 8 | length[1] & 0xff;
        byte[] packet = new byte[count];
        packet[0] = length[0];
        packet[1] = length[1];
        byte[] rest = input.readNBytes(count - 2);
        System.arraycopy(rest, 0, packet, 2, rest.length);
        return packet;
    }

    private static String readTgw(InputStream input) throws Exception {
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        int matched = 0;
        while (matched < 4) {
            int next = input.read();
            if (next < 0) throw new java.io.EOFException();
            value.write(next);
            int expected = switch (matched) {
                case 0, 2 -> '\r';
                default -> '\n';
            };
            matched = next == expected ? matched + 1 : (next == '\r' ? 1 : 0);
        }
        return value.toString(StandardCharsets.US_ASCII);
    }
}
