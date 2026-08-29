package cc.superbaby.happy4j.infrastructure.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XmlGameModeCatalogTest {
    @Test
    void readsEverySceneFromApkConfiguration() {
        var modes = new XmlGameModeCatalog().modes();
        assertThat(modes).hasSize(14);
        assertThat(modes).filteredOn(item -> item.enabled()).extracting("sceneId")
                .containsExactly(1260, 15260, 2260, 3260, 4260, 16260, 5260, 6260);
        assertThat(modes).allMatch(mode -> mode.maxBeans() >= 0);
        assertThat(modes).filteredOn(mode -> mode.sceneId() == 2260)
                .extracting("maxBeans").containsExactly(0L);
    }
}
