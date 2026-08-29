package cc.superbaby.happy4j.protocol.game;

public enum ReplaySection {
    ORDER(4, cc.superbaby.happy4j.domain.GameStage.CALLING_LANDLORD),
    DOUBLE(5, cc.superbaby.happy4j.domain.GameStage.DOUBLING),
    PLAY(7, cc.superbaby.happy4j.domain.GameStage.PLAYING),
    WAITING(-1, cc.superbaby.happy4j.domain.GameStage.READY);
    private final int code;
    private final cc.superbaby.happy4j.domain.GameStage stage;

    ReplaySection(int code, cc.superbaby.happy4j.domain.GameStage stage) {
        this.code = code;
        this.stage = stage;
    }

    public cc.superbaby.happy4j.domain.GameStage stage() {
        return stage;
    }

    public static ReplaySection fromCode(int code) {
        for (ReplaySection value : values()) if (value.code == code) return value;
        return WAITING;
    }
}
