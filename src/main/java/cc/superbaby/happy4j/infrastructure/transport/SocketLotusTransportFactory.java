package cc.superbaby.happy4j.infrastructure.transport;

import cc.superbaby.happy4j.application.port.GameTransportFactoryPort;
import cc.superbaby.happy4j.application.port.GameTransportPort;
import cc.superbaby.happy4j.config.Happy4jProperties;
import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;

import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
public final class SocketLotusTransportFactory implements GameTransportFactoryPort {
    private final Happy4jProperties.Game config;
    private final ProtocolRegistry schemas;
    private final ObjectMapper json;

    public SocketLotusTransportFactory(Happy4jProperties properties, ProtocolRegistry schemas, ObjectMapper json) {
        this.config = properties.game();
        this.schemas = schemas;
        this.json = json;
    }

    @Override
    public GameTransportPort create() {
        return new SocketLotusTransport(config, schemas, json);
    }
}
