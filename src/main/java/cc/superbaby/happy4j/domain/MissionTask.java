package cc.superbaby.happy4j.domain;

import java.time.Instant;

/**
 * Server mission state exposed as a stable UI model instead of an opaque activity JSON blob.
 */
public record MissionTask(int missionId, String title, int status, int periodFinishCount,
                          boolean claimable, String payload, Instant refreshedAt) {
    public MissionTask {
        title = title == null || title.isBlank() ? "任务 " + missionId : title;
        // wxpkg ActivityCenter uses status 2 for in-progress/actionable and status 3 for reward-ready.
        claimable = status == 3;
        payload = payload == null ? "{}" : payload;
        refreshedAt = refreshedAt == null ? Instant.EPOCH : refreshedAt;
    }
}
