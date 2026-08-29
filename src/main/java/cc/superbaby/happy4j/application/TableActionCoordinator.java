package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.domain.ClientSession;
import cc.superbaby.happy4j.domain.GameStage;
import cc.superbaby.happy4j.domain.TableAction;
import cc.superbaby.happy4j.domain.TableSnapshot;
import cc.superbaby.happy4j.protocol.game.GameSoType;
import cc.superbaby.happy4j.protocol.game.MiniGameGameSoCodec.GameSoEvent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serializes browser table commands until their authoritative GameSo acknowledgement arrives.
 */
@Component
public class TableActionCoordinator {
    private final ConcurrentHashMap<UUID, PendingAction> pending = new ConcurrentHashMap<>();

    public PendingAction claim(ClientSession session, TableAction action, String label) {
        PendingAction[] result = new PendingAction[1];
        session.updateTable(table -> {
            require(table, action);
            PendingAction value = new PendingAction(action, table.availableActions(), table.stage(),
                    table.prompt(), expectedType(action));
            if (pending.putIfAbsent(session.id(), value) != null)
                throw new IllegalStateException("上一项牌桌操作仍在等待服务器确认");
            result[0] = value;
            return state(table, table.stage(), List.of(), label + "提交中…");
        });
        return result[0];
    }

    public PendingAction current(UUID sessionId) {
        return pending.get(sessionId);
    }

    public void clear(UUID sessionId) {
        pending.remove(sessionId);
    }

    public void confirm(UUID sessionId, PendingAction action, GameSoEvent event, TableSnapshot table) {
        if (action != null && action.expectedType() == event.type() && event.actorSeat() == table.seatId())
            pending.remove(sessionId, action);
        if (table.stage() == GameStage.SETTLED) pending.remove(sessionId);
    }

    public void restore(ClientSession session, PendingAction action, String prompt) {
        if (action == null || !pending.remove(session.id(), action)) return;
        session.updateTable(current -> current.availableActions().isEmpty()
                ? state(current, action.stage(), action.actions(), prompt) : current);
    }

    private static void require(TableSnapshot table, TableAction action) {
        if (!table.availableActions().contains(action))
            throw new IllegalStateException("服务器当前未开放操作 " + action);
    }

    private static GameSoType expectedType(TableAction action) {
        return switch (action) {
            case CALL_LANDLORD, PASS_LANDLORD, ROB_LANDLORD, PASS_ROB -> GameSoType.ORDER;
            case DOUBLE, SUPER_DOUBLE, NO_DOUBLE -> GameSoType.DOUBLE_RESULT;
            case PLAY, PASS -> GameSoType.PLAY;
            case TRUST, CANCEL_TRUST -> GameSoType.TRUST;
            default -> GameSoType.UNKNOWN;
        };
    }

    private static TableSnapshot state(TableSnapshot value, GameStage stage,
                                       List<TableAction> actions, String prompt) {
        return new TableSnapshot(value.sceneId(), value.roomId(), value.tableId(), value.seatId(),
                value.turnSeatId(), value.landlordSeatId(), value.hand(), value.lastPlay(), value.lastPlayType(),
                value.lastPlayRank(), value.lastPlaySeatId(), value.bottomCards(), value.players(),
                value.actionHistory(), stage, actions, value.multiplier(), value.trusted(), value.settlement(),
                value.superDoubleItemId(), value.operationSeconds(), prompt);
    }

    public record PendingAction(TableAction action, List<TableAction> actions, GameStage stage,
                                String prompt, GameSoType expectedType) {
    }
}
