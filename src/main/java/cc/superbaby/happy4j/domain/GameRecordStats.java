package cc.superbaby.happy4j.domain;

public record GameRecordStats(
        long totalRounds,
        long wins,
        long losses,
        long draws,
        double winRate,
        long totalBeanChange,
        long bestWin,
        long worstLoss) {
    public static GameRecordStats empty() {
        return new GameRecordStats(0, 0, 0, 0, 0, 0, 0, 0);
    }
}
