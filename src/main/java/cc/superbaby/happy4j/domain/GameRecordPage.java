package cc.superbaby.happy4j.domain;

import java.util.List;

public record GameRecordPage(GameRecordStats stats, List<GameRecord> records) {
    public GameRecordPage {
        stats = stats == null ? GameRecordStats.empty() : stats;
        records = List.copyOf(records == null ? List.of() : records);
    }
}
