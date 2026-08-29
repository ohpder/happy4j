package cc.superbaby.happy4j.application.port;

import cc.superbaby.happy4j.domain.GameCredential;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface GameTransportPort extends AutoCloseable {
    void connect(GameCredential credential, Consumer<ProtocolResponse> eventSink);

    CompletableFuture<ProtocolResponse> request(ProtocolRequest request, Duration timeout);

    CompletableFuture<GrpcResponse> grpc(GrpcRequest request, Duration timeout);

    void send(ProtocolRequest request);

    boolean connected();

    void connectGameServer(List<GameServerEndpoint> endpoints);

    CompletableFuture<ProtocolResponse> gameRequest(ProtocolRequest request, Duration timeout);

    void gameSend(ProtocolRequest request);

    boolean gameConnected();

    @Override
    void close();

    record ProtocolRequest(
            int messageId,
            int destinationEntity,
            short destinationId,
            String requestType,
            String responseType,
            String json,
            boolean encrypted) {
    }

    record ProtocolResponse(
            int messageId,
            int messageType,
            int sequence,
            String schema,
            String json,
            byte[] payload) {
        public ProtocolResponse {
            payload = payload == null ? new byte[0] : payload.clone();
        }

        @Override
        public byte[] payload() {
            return payload.clone();
        }
    }

    record GrpcRequest(String fullMethodName, String groupName, String routeKey, byte[] body) {
        public GrpcRequest {
            if (fullMethodName == null || fullMethodName.isBlank()) {
                throw new IllegalArgumentException("gRPC fullMethodName is required");
            }
            groupName = groupName == null ? "" : groupName;
            routeKey = routeKey == null ? "" : routeKey;
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    record GrpcResponse(int sequence, String fullMethodName, int resultCode,
                        String resultMessage, byte[] body) {
        public GrpcResponse {
            fullMethodName = fullMethodName == null ? "" : fullMethodName;
            resultMessage = resultMessage == null ? "" : resultMessage;
            body = body == null ? new byte[0] : body.clone();
        }

        @Override
        public byte[] body() {
            return body.clone();
        }
    }

    record GameServerEndpoint(int serverId, String host, int port) {
        public GameServerEndpoint {
            if (serverId <= 0) throw new IllegalArgumentException("serverId must be positive");
            if (host == null || host.isBlank())
                throw new IllegalArgumentException("game server host is required");
            if (port < 1 || port > 65535)
                throw new IllegalArgumentException("game server port is invalid: " + port);
        }
    }
}
