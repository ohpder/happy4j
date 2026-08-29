package cc.superbaby.happy4j.application.port;

import java.util.List;

public interface MissionCatalogPort {
    List<Integer> ids();

    String title(int missionId);
}
