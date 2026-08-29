package cc.superbaby.happy4j.application;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

/**
 * Player-facing labels recovered from the shipped wxpkg mission catalog.
 * Remote MissionSvr status rows normally contain IDs and progress, but no description.
 */
@Component
public final class MissionLabelCatalog {
    private final Map<Integer, String> labels;

    public MissionLabelCatalog() {
        Properties values = new Properties();
        try (var input = MissionLabelCatalog.class.getResourceAsStream("/hlddz/mission-labels.properties")) {
            if (input == null) throw new IllegalStateException("mission label catalog is missing");
            values.load(new InputStreamReader(input, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("mission label catalog cannot be read", failure);
        }
        labels = values.stringPropertyNames().stream().collect(Collectors.toUnmodifiableMap(
                Integer::valueOf, values::getProperty));
    }

    public String title(int missionId) {
        return labels.getOrDefault(missionId, "");
    }
}
