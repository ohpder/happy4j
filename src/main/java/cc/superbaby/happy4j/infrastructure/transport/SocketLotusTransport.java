package cc.superbaby.happy4j.infrastructure.transport;

import cc.superbaby.happy4j.application.port.GameTransportPort;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.domain.GameCredential;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;
import cc.superbaby.happy4j.protocol.schema.LotusPayloadCodec;
import cc.superbaby.happy4j.protocol.wire.CMsgFrame;
import cc.superbaby.happy4j.protocol.wire.CMsgHeader;
import cc.superbaby.happy4j.protocol.wire.CcsFrameCodec;
import cc.superbaby.happy4j.protocol.wire.CcsHeader;
import cc.superbaby.happy4j.protocol.wire.CcsStreamDecoder;
import cc.superbaby.happy4j.protocol.wire.Compression;
import cc.superbaby.happy4j.protocol.wire.FunctionalEntity;
import cc.superbaby.happy4j.protocol.wire.MessageType;
import cc.superbaby.happy4j.protocol.generated.nativehlbase.PlayerInfo;
import cc.superbaby.happy4j.protocol.generated.nativehlserver.ControlHeaderReq;
import cc.superbaby.happy4j.protocol.generated.nativehlserver.GenericGrpcReq;
import cc.superbaby.happy4j.protocol.generated.nativehlserver.GenericGrpcRsp;

import com.google.protobuf.ByteString;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import tools.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * One live TSDK Lotus TCP connection with framed request correlation and push routing.
 */
final class SocketLotusTransport implements GameTransportPort {
    /**
     * TSDK::GRPC_MSGID initial value recovered from libTSDK .data at 0xb78e98.
     */
    static final int GRPC_MESSAGE_ID = 18493;
    private static final Logger LOG = LoggerFactory.getLogger(SocketLotusTransport.class);
    private final Happy4jProperties.Game config;
    private final LotusPayloadCodec payloads;
    private final ObjectMapper json;
    private final CcsFrameCodec codec = new CcsFrameCodec(256 * 1024);
    private final CcsStreamDecoder decoder = new CcsStreamDecoder(codec, 1024 * 1024);
    private final AtomicInteger sequence = new AtomicInteger((int) (System.currentTimeMillis() & 0x3fff_ffff));
    /**
     * CGRPCMsgTransaction owns an independent counter; CMsg sequence is assigned later by Lotus.
     */
    private final AtomicInteger grpcSequence = new AtomicInteger();
    private final AtomicBoolean running = new AtomicBoolean();
    private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
    private final Map<Integer, CompletableFuture<GrpcResponse>> grpcPending = new ConcurrentHashMap<>();
    private Socket socket;
    private OutputStream output;
    private GameCredential credential;
    private Consumer<ProtocolResponse> eventSink = ignored -> {
    };
    private SocketGameServerChannel game;

    SocketLotusTransport(Happy4jProperties.Game config, ProtocolRegistry schemas, ObjectMapper json) {
        this.config = config;
        this.json = json;
        this.payloads = new LotusPayloadCodec(schemas, json);
    }

    @Override
    public synchronized void connect(GameCredential credential, Consumer<ProtocolResponse> eventSink) {
        if (running.get()) return;
        cryptKey(credential);
        if (credential.sessionTicket().length > 0x7f) {
            throw new IllegalArgumentException("Lotus ST 超过 127 字节: " + credential.sessionTicket().length);
        }
        try {
            Socket next = new Socket();
            next.setTcpNoDelay(true);
            next.setKeepAlive(true);
            next.connect(new InetSocketAddress(config.lotusHost(), config.lotusPort()),
                    Math.toIntExact(config.connectTimeout().toMillis()));
            this.socket = next;
            this.output = next.getOutputStream();
            this.credential = credential;
            this.eventSink = eventSink == null ? ignored -> {
            } : eventSink;
            running.set(true);
            Thread reader = new Thread(this::readLoop, "lotus-reader-" + credential.uin());
            reader.setDaemon(true);
            reader.start();
            LOG.info("Lotus connected remote={}:{} uin={} accountType={} platform={} stBytes={} enKeyBytes=16",
                    config.lotusHost(), config.lotusPort(), credential.uin(), credential.accountType(),
                    platformCode(credential.accountType()), credential.sessionTicket().length);
        } catch (IOException e) {
            throw new IllegalStateException("连接 Lotus " + config.lotusHost() + ':' + config.lotusPort() + " 失败", e);
        }
    }

    @Override
    public CompletableFuture<ProtocolResponse> request(ProtocolRequest request, Duration timeout) {
        int seq = sequence.incrementAndGet();
        CompletableFuture<ProtocolResponse> future = new CompletableFuture<>();
        pending.put(seq, new Pending(request.responseType(), future));
        try {
            write(request, seq);
        } catch (RuntimeException failure) {
            pending.remove(seq);
            future.completeExceptionally(failure);
        }
        future.orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((ignored, failure) -> pending.remove(seq));
        return future;
    }

    @Override
    public void send(ProtocolRequest request) {
        write(request, sequence.incrementAndGet());
    }

    @Override
    public CompletableFuture<GrpcResponse> grpc(GrpcRequest request, Duration timeout) {
        int grpcSeq = grpcSequence.getAndIncrement();
        int transportSeq = sequence.incrementAndGet();
        CompletableFuture<GrpcResponse> future = new CompletableFuture<>();
        grpcPending.put(grpcSeq, future);
        try {
            PlayerInfo player = PlayerInfo.newBuilder()
                    .setGameId(config.id()).setUin((int) credential.uin())
                    .setAccountType(credential.accountType()).setClientType(config.clientType())
                    .setClientVersion(config.clientVersion())
                    .setOpenId(credential.openId()).setOpenKey(credential.openKey())
                    .build();
            ControlHeaderReq control = ControlHeaderReq.newBuilder()
                    .setPlayerinfo(player).setSeq(grpcSeq).setRoutekey(request.routeKey()).build();
            GenericGrpcReq envelope = GenericGrpcReq.newBuilder()
                    .setControl(control).setFullMethodName(request.fullMethodName())
                    .setBody(ByteString.copyFrom(request.body())).setGroupName(request.groupName()).build();
            writeGrpc(envelope.toByteArray(), transportSeq, grpcSeq, request.fullMethodName());
        } catch (RuntimeException failure) {
            grpcPending.remove(grpcSeq);
            future.completeExceptionally(failure);
        }
        future.orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                .whenComplete((ignored, failure) -> grpcPending.remove(grpcSeq));
        return future;
    }

    @Override
    public boolean connected() {
        return running.get() && socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public synchronized void connectGameServer(List<GameServerEndpoint> endpoints) {
        if (!connected()) throw new IllegalStateException("Lotus 尚未连接");
        if (game != null) game.close();
        game = new SocketGameServerChannel(config, payloads, json, credential, eventSink);
        game.connect(endpoints);
    }

    @Override
    public CompletableFuture<ProtocolResponse> gameRequest(ProtocolRequest request, Duration timeout) {
        SocketGameServerChannel current = game;
        if (current == null)
            return CompletableFuture.failedFuture(new IllegalStateException("游戏服务器尚未连接"));
        return current.request(request, timeout);
    }

    @Override
    public void gameSend(ProtocolRequest request) {
        SocketGameServerChannel current = game;
        if (current == null) throw new IllegalStateException("游戏服务器尚未连接");
        current.send(request);
    }

    @Override
    public boolean gameConnected() {
        return game != null && game.connected();
    }

    private synchronized void write(ProtocolRequest request, int seq) {
        if (!connected()) throw new IllegalStateException("Lotus 尚未连接");
        try {
            byte[] payload = payloads.encodeJson(request.requestType(), request.json());
            int flags = request.encrypted() ? CcsHeader.LOTUS_AUTHENTICATED_FLAGS : 0;
            CcsHeader outer = new CcsHeader(0, CcsHeader.TDR_PROTOCOL_VERSION, -1,
                    platformCode(credential.accountType()), credential.uin(),
                    flags | (Compression.NONE.id() << CcsHeader.COMPRESSION_SHIFT), credential.sessionTicket());
            CMsgHeader inner = CMsgHeader.of(request.messageId(), MessageType.REQUEST, seq,
                    FunctionalEntity.CLIENT, request.destinationEntity(), (short) -1, request.destinationId());
            byte[] packet = codec.encode(new CMsgFrame(outer, inner, payload),
                    request.encrypted() ? cryptKey(credential) : null);
            LOG.info("Lotus send msgId={} innerSeq={} outerSeq=-1 uin={} platform={} flags=0x{} stBytes={} payloadBytes={} packetBytes={}",
                    request.messageId(), seq, credential.uin(), outer.platform(), Integer.toHexString(outer.flags()),
                    outer.sessionTicket().length, payload.length, packet.length);
            output.write(packet);
            output.flush();
        } catch (IOException e) {
            closeWithFailure(e);
            throw new IllegalStateException("发送 Lotus 消息失败", e);
        }
    }

    private synchronized void writeGrpc(byte[] payload, int transportSeq, int grpcSeq, String method) {
        if (!connected()) throw new IllegalStateException("Lotus 尚未连接");
        try {
            CcsHeader outer = authenticatedHeader();
            CMsgHeader inner = CMsgHeader.of(GRPC_MESSAGE_ID, MessageType.REQUEST, transportSeq,
                    FunctionalEntity.CLIENT, FunctionalEntity.LOGIC_SERVICE_SERVER, (short) 0, (short) 0);
            byte[] packet = codec.encodeGrpc(new CMsgFrame(outer, inner, payload), cryptKey(credential));
            LOG.info("Lotus gRPC send method={} grpcSeq={} transportSeq={} msgId={} envelopeBytes={} packetBytes={}",
                    method, grpcSeq, transportSeq, GRPC_MESSAGE_ID, payload.length, packet.length);
            output.write(packet);
            output.flush();
        } catch (IOException e) {
            closeWithFailure(e);
            throw new IllegalStateException("发送 Lotus gRPC 消息失败", e);
        }
    }

    private CcsHeader authenticatedHeader() {
        return new CcsHeader(0, CcsHeader.TDR_PROTOCOL_VERSION, -1,
                platformCode(credential.accountType()), credential.uin(),
                CcsHeader.LOTUS_AUTHENTICATED_FLAGS, credential.sessionTicket());
    }

    private void readLoop() {
        byte[] chunk = new byte[16 * 1024];
        try (InputStream input = socket.getInputStream()) {
            while (running.get()) {
                int count = input.read(chunk);
                if (count < 0) throw new EOFException("Lotus 已关闭连接");
                byte[] exact = java.util.Arrays.copyOf(chunk, count);
                for (CMsgFrame frame : decoder.feed(exact, cryptKey(credential))) route(frame);
            }
        } catch (Exception e) {
            if (running.get()) closeWithFailure(e);
        }
    }

    private void route(CMsgFrame frame) {
        CMsgHeader header = frame.messageHeader();
        if (header.messageId() == GRPC_MESSAGE_ID && routeGrpc(header, frame.payload())) return;
        Pending match = pending.remove(header.sequence());
        String schema = match == null ? "" : match.schema;
        String body;
        try {
            body = schema.isBlank() ? "" : payloads.decodeJson(schema, frame.payload());
        } catch (RuntimeException decodeFailure) {
            body = "{\"decodeError\":\"" + escape(decodeFailure.getMessage()) + "\"}";
        }
        ProtocolResponse response = new ProtocolResponse(header.messageId(), header.messageType(), header.sequence(),
                schema, body, frame.payload());
        if (match != null) match.future.complete(response);
        else eventSink.accept(response);
    }

    private boolean routeGrpc(CMsgHeader header, byte[] payload) {
        try {
            GenericGrpcRsp envelope = GenericGrpcRsp.parseFrom(payload);
            int grpcSeq = envelope.hasControl() ? envelope.getControl().getSeq() : header.sequence();
            int code = envelope.hasControl() && envelope.getControl().hasResult()
                    ? envelope.getControl().getResult().getCode() : 0;
            String message = envelope.hasControl() && envelope.getControl().hasResult()
                    ? envelope.getControl().getResult().getMessage() : "";
            GrpcResponse response = new GrpcResponse(grpcSeq, envelope.getFullMethodName(), code,
                    message, envelope.getBody().toByteArray());
            CompletableFuture<GrpcResponse> match = grpcPending.remove(grpcSeq);
            if (match == null) match = grpcPending.remove(header.sequence());
            LOG.info("Lotus gRPC recv method={} grpcSeq={} result={} bodyBytes={}",
                    response.fullMethodName(), response.sequence(), response.resultCode(), response.body().length);
            if (match != null) match.complete(response);
            else
                eventSink.accept(new ProtocolResponse(header.messageId(), header.messageType(), header.sequence(),
                        "Native.HLServer.GenericGrpcRsp", "{\"grpcMethod\":\"" + escape(response.fullMethodName())
                        + "\",\"resultCode\":" + response.resultCode() + "}", payload));
            return true;
        } catch (Exception failure) {
            LOG.warn("Lotus gRPC response decode failed: {}", failure.toString());
            return false;
        }
    }

    static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Exact word_93AE60[accountType] mapping used by CLotusSvrConnect::AutoFillCSHead.
     */
    static short platformCode(int accountType) {
        return switch (accountType) {
            case 0 -> (short) -1;
            case 1, 2, 3 -> (short) -3;
            case 4 -> (short) -4;
            default -> 0;
        };
    }

    /**
     * Lotus packet crypto uses the decrypted SafeLogin enckey, not the request random key.
     */
    static byte[] cryptKey(GameCredential credential) {
        byte[] raw = credential.enKey() == null ? new byte[0]
                : credential.enKey().getBytes(StandardCharsets.ISO_8859_1);
        int nul = 0;
        while (nul < raw.length && raw[nul] != 0) nul++;
        byte[] key = Arrays.copyOf(raw, nul);
        if (key.length < 16) {
            throw new IllegalArgumentException("Lotus enckey 至少需要 16 字节，实际 " + key.length);
        }
        return Arrays.copyOf(key, 16);
    }

    private synchronized void closeWithFailure(Exception failure) {
        if (!running.getAndSet(false)) return;
        LOG.warn("Lotus disconnected uin={} remote={}:{} cause={}",
                credential == null ? 0 : credential.uin(), config.lotusHost(), config.lotusPort(), failure.toString());
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        SocketGameServerChannel currentGame = game;
        if (currentGame != null) currentGame.close();
        pending.values().forEach(item -> item.future.completeExceptionally(failure));
        pending.clear();
        grpcPending.values().forEach(item -> item.completeExceptionally(failure));
        grpcPending.clear();
        eventSink.accept(new ProtocolResponse(0, -1, 0, "", "{\"transportError\":\"" + escape(failure.getMessage()) + "\"}", new byte[0]));
    }

    @Override
    public void close() {
        closeWithFailure(new IOException("客户端关闭 Lotus 连接"));
    }

    private record Pending(String schema, CompletableFuture<ProtocolResponse> future) {
    }
}
