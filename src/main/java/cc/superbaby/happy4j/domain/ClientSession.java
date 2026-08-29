package cc.superbaby.happy4j.domain;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Session aggregate: owns the complete login, hall, room and table state machine.
 */
public final class ClientSession {
    private static final int MAX_EVENTS = 300;
    private final UUID id;
    private final UUID ownerId;
    private final Instant createdAt;
    private final AtomicLong eventIndex = new AtomicLong();
    private final ArrayDeque<RemoteEvent> events = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Consumer<RemoteEvent>> subscribers = new CopyOnWriteArrayList<>();
    private final Map<String, ActivityView> activities = new LinkedHashMap<>();
    private final Map<Integer, MissionTask> missions = new LinkedHashMap<>();
    private final Map<Integer, InventoryItem> inventory = new LinkedHashMap<>();
    private ClientPhase phase = ClientPhase.CREATED;
    private String detail = "等待扫码";
    private String qrId;
    private String qrProvider;
    private PlatformCredential platformCredential;
    private GameCredential gameCredential;
    private WalletBalance wallet = WalletBalance.empty();
    private PlayerProfile profile = PlayerProfile.empty();
    private TableSnapshot table = TableSnapshot.empty();

    public ClientSession(UUID id, UUID ownerId) {
        this.id = id;
        this.ownerId = java.util.Objects.requireNonNull(ownerId, "ownerId");
        this.createdAt = Instant.now();
        publish("SESSION", "客户端会话已创建", null);
    }

    public UUID id() {
        return id;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public synchronized ClientPhase phase() {
        return phase;
    }

    public synchronized String qrId() {
        return qrId;
    }

    public synchronized String qrProvider() {
        return qrProvider;
    }

    public synchronized PlatformCredential platformCredential() {
        return platformCredential;
    }

    public synchronized GameCredential gameCredential() {
        return gameCredential;
    }

    public synchronized WalletBalance wallet() {
        return wallet;
    }

    public synchronized PlayerProfile profile() {
        return profile;
    }

    public synchronized TableSnapshot table() {
        return table;
    }

    public synchronized void transition(ClientPhase next, String message) {
        phase = next;
        detail = message;
        publish("STATE", message, Map.of("phase", next));
    }

    public synchronized void qr(String provider, String value) {
        qrProvider = provider;
        qrId = value;
    }

    public synchronized void platformCredential(PlatformCredential value) {
        platformCredential = value;
    }

    public synchronized void gameCredential(GameCredential value) {
        gameCredential = value;
        wallet = value == null ? WalletBalance.empty()
                : new WalletBalance(value.beans(), value.diamonds(), 0, Instant.EPOCH);
    }

    public synchronized void wallet(WalletBalance value) {
        wallet = value == null ? WalletBalance.empty() : value;
        publish("WALLET", "余额已刷新", wallet);
    }

    public synchronized void profile(PlayerProfile value) {
        profile = value == null ? PlayerProfile.empty() : value;
        publish("PROFILE", "玩家资料已刷新", profile);
    }

    public synchronized void missions(List<MissionTask> values) {
        missions.clear();
        if (values != null) values.forEach(value -> missions.put(value.missionId(), value));
        publish("MISSIONS", "任务状态已刷新", new ArrayList<>(missions.values()));
    }

    public synchronized void inventory(List<InventoryItem> values) {
        inventory.clear();
        if (values != null) values.forEach(value -> inventory.put(value.itemId(), value));
        publish("INVENTORY", "道具数量已刷新", new ArrayList<>(inventory.values()));
    }

    public synchronized void inventoryItem(int itemId, String name, long count) {
        inventory.put(itemId, new InventoryItem(itemId, name, count, Instant.now()));
        publish("INVENTORY", "道具数量已更新", new ArrayList<>(inventory.values()));
    }

    public synchronized long itemCount(int itemId) {
        InventoryItem item = inventory.get(itemId);
        return item == null ? 0 : item.count();
    }

    public synchronized void table(TableSnapshot value) {
        table = value;
        publish("TABLE", value.prompt(), value);
    }

    /**
     * Atomically reduces table state so HTTP actions cannot overwrite newer socket events.
     */
    public synchronized TableSnapshot updateTable(UnaryOperator<TableSnapshot> reducer) {
        TableSnapshot next = reducer.apply(table);
        if (next == null) throw new IllegalArgumentException("table reducer returned null");
        table = next;
        publish("TABLE", next.prompt(), next);
        return next;
    }

    public synchronized void activity(ActivityView value) {
        activities.put(value.id(), value);
        publish("ACTIVITY", value.title(), value);
    }

    public void publish(String kind, String message, Object data) {
        RemoteEvent event = new RemoteEvent(eventIndex.incrementAndGet(), kind, message, data, Instant.now());
        synchronized (this) {
            events.addLast(event);
            while (events.size() > MAX_EVENTS) events.removeFirst();
        }
        subscribers.forEach(listener -> listener.accept(event));
    }

    public AutoCloseable subscribe(Consumer<RemoteEvent> listener) {
        subscribers.add(listener);
        return () -> subscribers.remove(listener);
    }

    public synchronized List<RemoteEvent> eventsAfter(long index) {
        return events.stream().filter(event -> event.index() > index).toList();
    }

    public synchronized Snapshot snapshot() {
        long uin = gameCredential == null ? 0 : gameCredential.uin();
        return new Snapshot(id, phase, detail, uin, profile.nickname(), profile.avatarUrl(), profile.gender(),
                wallet.beans(), wallet.diamonds(), wallet.goldCoins(),
                wallet.refreshedAt(), createdAt,
                new ArrayList<>(activities.values()), new ArrayList<>(missions.values()),
                new ArrayList<>(inventory.values()), table);
    }

    public record Snapshot(
            UUID id,
            ClientPhase phase,
            String detail,
            long uin,
            String nickname,
            String avatarUrl,
            int gender,
            long beans,
            long diamonds,
            long goldCoins,
            Instant walletRefreshedAt,
            Instant createdAt,
            List<ActivityView> activities,
            List<MissionTask> missions,
            List<InventoryItem> inventory,
            TableSnapshot table) {
    }
}
