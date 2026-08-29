package cc.superbaby.happy4j.infrastructure.transport;

import cc.superbaby.happy4j.application.port.GameTransportPort.GameServerEndpoint;
import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolRequest;
import cc.superbaby.happy4j.application.port.GameTransportPort.ProtocolResponse;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.protocol.schema.LotusPayloadCodec;
import cc.superbaby.happy4j.protocol.game.GameMessage;
import cc.superbaby.happy4j.protocol.wire.CMsgFrame;
import cc.superbaby.happy4j.protocol.wire.CMsgHeader;
import cc.superbaby.happy4j.protocol.wire.CcsFrameCodec;
import cc.superbaby.happy4j.protocol.wire.CcsHeader;
import cc.superbaby.happy4j.protocol.wire.CcsStreamDecoder;
import cc.superbaby.happy4j.protocol.wire.FunctionalEntity;
import cc.superbaby.happy4j.protocol.wire.MessageType;
import cc.superbaby.happy4j.protocol.wire.TdrBufferWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Independent TSDK GameSvr TCP channel established after QuickGame/AppDir resolution.
 */
final class SocketGameServerChannel implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(SocketGameServerChannel.class);
    private static final String TGW_DOMAIN = "happyapp.huanle.qq.com";
    private static final int TGW_PORT = 443;
    /** ModuleJSAppRoot.TSDKServiceInGame.NEW_HLDDZ_CAPABILITY without optional hero-card bits. */
    /** CEnterRoomCommand::SetEnterRoomTLVEx ORs the JS capability with 0x1000. */
    /**
     * Verified against a live device capture: EnterRoomByQuick logged
     * "CAPABILITY is: 4574385884827258521" for a login that returned resultID 0.
     */
    private static final long CLIENT_CAPABILITY = 0x3f7b7bb9e2b08e99L;

    private final Happy4jProperties.Game config;
    private final LotusPayloadCodec payloads;
    private final ObjectMapper json;
    private final CcsFrameCodec frames = new CcsFrameCodec(4 * 1024 * 1024);
    private final AtomicInteger sequence = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final GameCredential credential;
    private final Consumer<ProtocolResponse> eventSink;
    private volatile long playerId = -1;
    private volatile Socket socket;
    private volatile OutputStream output;
    private volatile CcsStreamDecoder decoder;
    private volatile GameServerEndpoint endpoint;
    private volatile java.util.concurrent.ScheduledExecutorService heartbeat;

    SocketGameServerChannel(Happy4jProperties.Game config, LotusPayloadCodec payloads, ObjectMapper json,
                            GameCredential credential, Consumer<ProtocolResponse> eventSink) {
        this.config = config;
        this.payloads = payloads;
        this.json = json;
        this.credential = credential;
        this.eventSink = eventSink == null ? ignored -> {
        } : eventSink;
    }

    synchronized void connect(List<GameServerEndpoint> endpoints) {
        if (connected()) return;
        if (endpoints == null || endpoints.isEmpty())
            throw new IllegalArgumentException("游戏服务器候选地址为空");
        Exception last = null;
        for (GameServerEndpoint candidate : endpoints) {
            try {
                Socket next = new Socket();
                next.setTcpNoDelay(true);
                next.setKeepAlive(true);
                next.connect(new InetSocketAddress(candidate.host(), candidate.port()),
                        Math.toIntExact(config.connectTimeout().toMillis()));
                this.socket = next;
                this.output = next.getOutputStream();
                this.decoder = new CcsStreamDecoder(frames, 4 * 1024 * 1024);
                this.endpoint = candidate;
                this.playerId = credential.playerId() > 0 ? credential.playerId() : -1;
                running.set(true);
                writeTgwHandshake();
                writeBookRouter();
                Thread reader = new Thread(this::readLoop, "game-server-reader-" + credential.uin());
                reader.setDaemon(true);
                reader.start();
                startHeartbeat();
                LOG.info("GameSvr connected routeServer={} remote={}:{} uin={} outerSeq={} protocol=TDR",
                        candidate.serverId(), candidate.host(), candidate.port(), credential.uin(), playerId);
                return;
            } catch (Exception failure) {
                last = failure;
                running.set(false);
                try {
                    if (socket != null) socket.close();
                } catch (IOException ignored) {
                }
                socket = null;
                output = null;
            }
        }
        throw new IllegalStateException("全部游戏服务器地址连接失败", last);
    }

    CompletableFuture<ProtocolResponse> request(ProtocolRequest request, Duration timeout) {
        int seq = sequence.incrementAndGet();
        CompletableFuture<ProtocolResponse> future = new CompletableFuture<>();
        pending.put(seq, new Pending(request.messageId(), request.responseType(), future));
        try {
            write(request, seq);
        } catch (RuntimeException failure) {
            pending.remove(seq);
            future.completeExceptionally(failure);
        }
        future.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                .whenComplete((ignored, failure) -> pending.remove(seq));
        return future;
    }

    void send(ProtocolRequest request) {
        write(request, sequence.incrementAndGet());
    }

    boolean connected() {
        Socket current = socket;
        return running.get() && current != null && current.isConnected() && !current.isClosed();
    }

    private void writeTgwHandshake() throws IOException {
        String value = "tgw_l7_forward\r\nHost: gamesvrid" + endpoint.serverId() + '.' + TGW_DOMAIN + ':' + TGW_PORT
                + "\r\n\r\n";
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * CEnterRoomCommand::RequestBookRouter sends message 7601 before RequestLogin.
     * CReqBookRoutePara::construct zero-initializes the object then sets a QWORD at offset+20
     * to 0x40000; RequestBookRouter only overwrites 4 of the 6 TDR fields (uin, ability=1,
     * A0=0, SvrId), leaving the fifth field (offset+22, "0x0004" byte-split from that QWORD)
     * at its constructed value of 4 — verified against the decompiled binary byte-for-byte.
     */
    private void writeBookRouter() throws IOException {
        TdrBufferWriter payload = new TdrBufferWriter(16);
        payload.writeInt32((int) credential.uin()).writeInt32(0)
                .writeInt16(1).writeInt16(4).writeInt16(0).writeInt16(endpoint.serverId());
        CMsgHeader inner = CMsgHeader.of(7601, MessageType.NOTIFY, 0,
                FunctionalEntity.CLIENT, FunctionalEntity.CLIENT, (short) 0, (short) 0);
        output.write(frames.encode(new CMsgFrame(authenticatedHeader(), inner, payload.toByteArray()),
                SocketLotusTransport.cryptKey(credential)));
        output.flush();
    }

    /**
     * The WeChat package sets {@code Sqa.Foe=10000}: the first 15812 hello is delayed ten
     * seconds and subsequent receives re-arm the same ten-second timer.  Starting it at zero
     * and repeating every two seconds raced room login/sit-down and produced a stream of
     * heartbeat replies while the malformed 15803 was waiting.
     */
    private void startHeartbeat() {
        heartbeat = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "game-server-heartbeat-" + credential.uin());
            thread.setDaemon(true);
            return thread;
        });
        heartbeat.scheduleAtFixedRate(this::writeHeartbeatSafely, 10, 10,
                java.util.concurrent.TimeUnit.SECONDS);
    }

    private void writeHeartbeatSafely() {
        try {
            writeHeartbeat();
        } catch (Exception failure) {
            LOG.warn("GameSvr heartbeat failed uin={} cause={}", credential.uin(), failure.getMessage());
        }
    }

    private synchronized void writeHeartbeat() throws IOException {
        if (!connected()) return;
        int seq = sequence.incrementAndGet();
        CMsgHeader inner = CMsgHeader.of(GameMessage.HEARTBEAT.id(), MessageType.REQUEST, 12345,
                FunctionalEntity.CLIENT, FunctionalEntity.CLIENT, (short) 0, (short) 0);
        output.write(frames.encode(new CMsgFrame(authenticatedHeader(), inner, new byte[0]),
                SocketLotusTransport.cryptKey(credential)));
        output.flush();
        LOG.info("GameSvr send msgId=15812 seq={} (heartbeat)", seq);
    }

    private synchronized void write(ProtocolRequest request, int seq) {
        if (!connected()) throw new IllegalStateException("游戏服务器尚未连接");
        try {
            String requestJson = request.requestType().endsWith("CRequestLoginHGPara")
                    ? enrichLoginTlvs(request.json()) : request.json();
            byte[] payload = payloads.encodeJson(request.requestType(), requestJson);
            // CGamesvrActiveTransaction::RequestSitDown/RequestReady/RequestStandUp all address
            // FunctionalEntity.ROOM using playerBaseInfo+8 (the PlayerID assigned by the Login
            // response) as destinationId, refreshed on every call — not a value baked in earlier.
            // nf is [msgId,type,seq,srcEntity,dstEntity,srcId,dstId]. wxpkg GameSvr.Rf/Qqa
            // zeroes both entity/id routing fields for 15801/15803/15806/15810/15813.
            // Generic Lotus server routing (dstEntity=1,dstId=-1) was tolerated by ordinary
            // actions but 15806 was silently discarded and therefore expired locally.
            int destinationEntity = request.destinationEntity() == FunctionalEntity.SERVER
                    ? FunctionalEntity.CLIENT : request.destinationEntity();
            short destinationId = request.destinationEntity() == FunctionalEntity.ROOM
                    ? (short) playerId
                    : request.destinationEntity() == FunctionalEntity.SERVER
                    ? (short) 0 : request.destinationId();
            int innerSequence = GameMessage.QUERY_REPLAY.matches(request.messageId()) ? 0 : 131415;
            CMsgHeader inner = CMsgHeader.of(request.messageId(), MessageType.REQUEST, innerSequence,
                    FunctionalEntity.CLIENT, destinationEntity, (short) 0, destinationId);
            // Qqa is the PB-only exception in the otherwise native-TDR Java GameSvr path.
            // wxpkg sets If.VJa and therefore CCS protocol version 23 for its 08 02 request.
            CcsHeader outer = authenticatedHeader(GameMessage.QUERY_REPLAY.matches(request.messageId())
                    ? CcsHeader.PROTOBUF_PROTOCOL_VERSION : CcsHeader.TDR_PROTOCOL_VERSION);
            byte[] frame = frames.encode(new CMsgFrame(outer, inner, payload),
                    request.encrypted() ? SocketLotusTransport.cryptKey(credential) : null);
            LOG.warn("GameSvr send msgId={} outer={} inner=[destEntity={} destId={}] payloadHex={} frameHex={}",
                    request.messageId(), outer, inner.destinationEntity(), inner.destinationId(),
                    java.util.HexFormat.of().formatHex(payload), java.util.HexFormat.of().formatHex(frame));
            output.write(frame);
            output.flush();
            LOG.info("GameSvr send msgId={} seq={} outerSeq={} payloadBytes={} remote={}:{}",
                    request.messageId(), seq, playerId, payload.length, endpoint.host(), endpoint.port());
        } catch (IOException failure) {
            closeWithFailure(failure, true);
            throw new IllegalStateException("发送游戏服务器消息失败", failure);
        }
    }

    /**
     * CEnterRoomCommand::GetEnterRoomTLV always prepends capability, device and route TLVs.
     */
    private String enrichLoginTlvs(String source) {
        try {
            JsonNode root = json.readTree(source);
            List<Map<String, Object>> values = new ArrayList<>();
            values.add(tlv(10012, new TdrBufferWriter(8).writeInt64(CLIENT_CAPABILITY).toByteArray()));

            String deviceId = String.valueOf(credential.raw().getOrDefault("deviceID",
                    "happy4j-" + Long.toUnsignedString(credential.uin())));
            byte[] device = deviceId.getBytes(StandardCharsets.UTF_8);
            if (device.length > 128) device = java.util.Arrays.copyOf(device, 128);
            values.add(tlv(5, new TdrBufferWriter(2 + device.length)
                    .writeInt16(device.length).writeBytes(device).toByteArray()));

            byte[] host = endpoint.host().getBytes(StandardCharsets.US_ASCII);
            if (host.length > 48)
                throw new IllegalStateException("GameSvr route host exceeds 48 bytes");
            TdrBufferWriter route = new TdrBufferWriter(8 + host.length * 2);
            route.writeInt16(host.length).writeBytes(host).writeInt16(endpoint.port())
                    .writeInt16(host.length).writeBytes(host).writeInt16(endpoint.port());
            values.add(tlv(7, route.toByteArray()));

            JsonNode existing = root.path("TLVInfo");
            if (existing.isArray()) for (JsonNode item : existing) {
                @SuppressWarnings("unchecked") Map<String, Object> value = json.convertValue(item, Map.class);
                values.add(value);
            }
            if (values.size() > 32)
                throw new IllegalStateException("GameSvr login TLV count exceeds 32");
            tools.jackson.databind.node.ObjectNode object = (tools.jackson.databind.node.ObjectNode) root;
            object.put("TLVNum", values.size());
            object.set("TLVInfo", json.valueToTree(values));
            LOG.info("GameSvr login TLVs count={} capability=0x{} deviceBytes={} route={}:{}",
                    values.size(), Long.toHexString(CLIENT_CAPABILITY), device.length, endpoint.host(), endpoint.port());
            return json.writeValueAsString(object);
        } catch (Exception failure) {
            throw new IllegalStateException("构造 GameSvr 登录 TLV 失败", failure);
        }
    }

    private static Map<String, Object> tlv(int type, byte[] value) {
        return Map.of("Type", type, "Len", value.length,
                "Value", Base64.getEncoder().encodeToString(value));
    }

    private CcsHeader authenticatedHeader() {
        return authenticatedHeader(CcsHeader.TDR_PROTOCOL_VERSION);
    }

    private CcsHeader authenticatedHeader(int protocolVersion) {
        return new CcsHeader(0, protocolVersion, (int) playerId,
                SocketLotusTransport.platformCode(credential.accountType()), credential.uin(),
                CcsHeader.LOTUS_AUTHENTICATED_FLAGS, credential.sessionTicket());
    }

    private void readLoop() {
        byte[] chunk = new byte[16 * 1024];
        try (InputStream input = socket.getInputStream()) {
            while (running.get()) {
                int count = input.read(chunk);
                if (count < 0) throw new EOFException("GameSvr 已关闭连接");
                byte[] exact = java.util.Arrays.copyOf(chunk, count);
                LOG.warn("GameSvr raw read bytes={} hex={}", count,
                        java.util.HexFormat.of().formatHex(exact, 0, Math.min(count, 96)));
                List<CMsgFrame> frames = decoder.feed(exact, SocketLotusTransport.cryptKey(credential));
                LOG.warn("GameSvr decoded frameCount={}", frames.size());
                for (CMsgFrame frame : frames) route(frame);
            }
        } catch (Exception failure) {
            if (running.get()) closeWithFailure(failure, true);
        }
    }

    private void route(CMsgFrame frame) {
        CMsgHeader header = frame.messageHeader();
        // RequestBookRouter (7601) is fire-and-forget today — its response was never inspected.
        // Dump the raw bytes so a routing-level rejection isn't silently masked as a later
        // generic CRequestLoginHGPara failure.
        if (header.messageId() == 7601) {
            LOG.warn("GameSvr BookRouter response seq={} payloadHex={}", header.sequence(),
                    java.util.HexFormat.of().formatHex(frame.payload()));
        }
        if (GameMessage.CALL_PLAYER.matches(header.messageId()) && unwrapCallPlayer(frame)) return;
        Pending match = pending.get(header.sequence());
        // GameSvr pushes can reuse the active transaction sequence. Correlation therefore
        // requires both sequence and message ID; otherwise a 15814 kick/status push can be
        // mis-decoded as the pending 15801 login response.
        if (match != null && match.messageId == header.messageId()) {
            pending.remove(header.sequence(), match);
        } else {
            match = null;
        }
        if (match == null) match = pending.entrySet().stream()
                .filter(entry -> entry.getValue().messageId == header.messageId())
                .findFirst().map(entry -> pending.remove(entry.getKey())).orElse(null);
        String schema = match == null ? "" : match.schema;
        String body = schema.isBlank() ? "" : payloads.decodeJson(schema, frame.payload());
        ProtocolResponse response = new ProtocolResponse(header.messageId(), header.messageType(), header.sequence(),
                schema, body, frame.payload());
        LOG.info("GameSvr recv msgId={} seq={} matched={} payloadBytes={}",
                header.messageId(), header.sequence(), match != null, frame.payload().length);
        if (match != null) {
            updatePlayerId(schema, body);
            match.future.complete(response);
        } else {
            eventSink.accept(response);
        }
    }

    /**
     * GameSvr returns one or more application messages inside 15814.  The first i16 is the
     * message count (the previous implementation misnamed it notifyType), followed by that many
     * {@code u16 CMsgLength + CMsgHead + CUnknowMsgPara} values.  Wxpkg's Wqa loop uses exactly
     * this count.  A reconnect snapshot commonly arrives as count=4 and must not be discarded.
     */
    private boolean unwrapCallPlayer(CMsgFrame wrapper) {
        byte[] payload = wrapper.payload();
        if (payload.length < 2) return false;
        ByteBuffer input = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int messageCount = Short.toUnsignedInt(input.getShort());
        if (messageCount < 1 || messageCount > 64) {
            LOG.warn("GameSvr unwrap msg15814 FAILED messageCount={} payloadBytes={} payloadHex={}",
                    messageCount, payload.length,
                    java.util.HexFormat.of().formatHex(payload, 0, Math.min(payload.length, 64)));
            return false;
        }
        for (int index = 0; index < messageCount; index++) {
            if (input.remaining() < 2) return false;
            int messageLength = Short.toUnsignedInt(input.getShort());
            if (messageLength < CMsgHeader.WIRE_SIZE + 2 || messageLength > input.remaining())
                return false;
            int limit = input.position() + messageLength;
            CMsgHeader embeddedHeader = CMsgHeader.decode(input);
            int parameterLength = Short.toUnsignedInt(input.getShort());
            if (parameterLength < 2 || parameterLength - 2 > limit - input.position()) return false;
            byte[] embeddedPayload = new byte[parameterLength - 2];
            input.get(embeddedPayload);
            input.position(limit);
            LOG.info("GameSvr unwrap msg15814 item={}/{} -> msgId={} seq={} payloadBytes={}",
                    index + 1, messageCount, embeddedHeader.messageId(), embeddedHeader.sequence(),
                    embeddedPayload.length);
            route(new CMsgFrame(wrapper.transportHeader(), embeddedHeader, embeddedPayload));
        }
        return true;
    }

    private void updatePlayerId(String schema, String body) {
        if (!schema.endsWith("CResponseLoginHGPara")) return;
        try {
            JsonNode root = json.readTree(body);
            long next = root.path("PlayerID").asLong(-1);
            if (next > 0) playerId = next;
        } catch (Exception ignored) {
        }
    }

    private synchronized void closeWithFailure(Exception failure, boolean publish) {
        if (!running.getAndSet(false)) return;
        if (heartbeat != null) heartbeat.shutdownNow();
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        pending.values().forEach(item -> item.future.completeExceptionally(failure));
        pending.clear();
        if (publish) eventSink.accept(new ProtocolResponse(0, -1, 0, "GameServer.Transport",
                "{\"gameTransportError\":\"" + SocketLotusTransport.escape(failure.getMessage()) + "\"}",
                new byte[0]));
    }

    @Override
    public void close() {
        closeWithFailure(new IOException("客户端关闭游戏服务器连接"), false);
    }

    private record Pending(int messageId, String schema,
                           CompletableFuture<ProtocolResponse> future) {
    }
}
