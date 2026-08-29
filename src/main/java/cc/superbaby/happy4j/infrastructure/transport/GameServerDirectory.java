package cc.superbaby.happy4j.infrastructure.transport;

import cc.superbaby.happy4j.application.port.GameTransportPort.GameServerEndpoint;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parser and resolver for the AppDir-delivered GameSvrList.ini used by TSDK.
 */
public final class GameServerDirectory {
    private static final int FALLBACK_SERVER_ID = 30000;
    private final List<Integer> ports;
    private final Map<Integer, ServerNode> servers;

    private GameServerDirectory(List<Integer> ports, Map<Integer, ServerNode> servers) {
        this.ports = List.copyOf(ports);
        this.servers = Map.copyOf(servers);
    }

    public static GameServerDirectory parse(byte[] content) {
        return parse(new String(content, StandardCharsets.UTF_8));
    }

    public static GameServerDirectory parse(String content) {
        Map<String, Map<String, String>> sections = new LinkedHashMap<>();
        String section = "";
        for (String original : content.replace("\ufeff", "").split("\\R")) {
            String line = original.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith(";")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length() - 1).trim();
                sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>());
                continue;
            }
            int separator = line.indexOf('=');
            if (separator > 0) sections.computeIfAbsent(section, ignored -> new LinkedHashMap<>())
                    .put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }

        List<Integer> ports = splitValues(sections.getOrDefault("AppInfo", Map.of()).get("port")).stream()
                .map(Integer::parseInt).filter(value -> value > 0 && value <= 65535).toList();
        if (ports.isEmpty())
            throw new IllegalArgumentException("GameSvrList AppInfo.port is empty");

        Map<Integer, ServerNode> servers = new LinkedHashMap<>();
        sections.forEach((name, values) -> {
            if (!name.startsWith("Server") || !values.containsKey("ServerID")) return;
            int serverId = Integer.parseInt(values.get("ServerID"));
            List<Map.Entry<Integer, String>> levels = values.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith("IPLevel"))
                    .map(entry -> Map.entry(Integer.parseInt(entry.getKey().substring(7)), entry.getValue()))
                    .sorted(Comparator.comparingInt(Map.Entry::getKey)).toList();
            List<String> hosts = levels.stream().flatMap(entry -> splitValues(entry.getValue()).stream()).toList();
            if (!hosts.isEmpty()) servers.put(serverId, new ServerNode(serverId, hosts));
        });
        if (servers.isEmpty())
            throw new IllegalArgumentException("GameSvrList contains no server nodes");
        return new GameServerDirectory(ports, servers);
    }

    /**
     * TSDK's GetGameSvrNodeFromMap uses ServerID 30000 when an assigned ID is absent.
     */
    public List<GameServerEndpoint> resolve(int requestedServerId) {
        ServerNode node = servers.get(requestedServerId);
        if (node == null) node = servers.get(FALLBACK_SERVER_ID);
        if (node == null)
            throw new IllegalArgumentException("GameSvrList lacks server " + requestedServerId
                    + " and fallback " + FALLBACK_SERVER_ID);
        List<GameServerEndpoint> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (String host : node.hosts())
            for (int port : ports) {
                // The fallback node chooses the socket address only. TGW Host and BookRouter must
                // retain the QuickGame-assigned ID (for example 7540), not fallback node 30000.
                if (unique.add(host + ':' + port))
                    result.add(new GameServerEndpoint(requestedServerId, host, port));
            }
        return List.copyOf(result);
    }

    private static List<String> splitValues(String value) {
        if (value == null) return List.of();
        List<String> result = new ArrayList<>();
        for (String item : value.split(";")) if (!item.isBlank()) result.add(item.trim());
        return result;
    }

    private record ServerNode(int serverId, List<String> hosts) {
    }
}
