package cc.superbaby.happy4j.infrastructure.config;

import cc.superbaby.happy4j.domain.GameMode;

import org.springframework.core.io.ClassPathResource;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads scene IDs and limits from the APK's own michallExtranet.xml.
 */
public final class XmlGameModeCatalog {
    private final List<GameMode> modes;

    public XmlGameModeCatalog() {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var document = factory.newDocumentBuilder().parse(
                    new ClassPathResource("hlddz/michallExtranet.xml").getInputStream());
            NodeList nodes = document.getElementsByTagName("NewSceneNode").item(0).getChildNodes();
            List<GameMode> result = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                if (!(nodes.item(i) instanceof Element element) || !element.getTagName().equals("Node"))
                    continue;
                int sceneId = intAttr(element, "SceneID");
                String name = element.getAttribute("SceneName");
                String family = name.contains("癞子") ? "LAIZI" : (name.contains("挑战") ? "CHALLENGE" : "CLASSIC");
                result.add(new GameMode(sceneId, family, name, longAttr(element, "minbean"),
                        Math.max(0, longAttr(element, "maxbean")), longAttr(element, "fengding"),
                        intAttr(element, "difen"), Boolean.parseBoolean(element.getAttribute("isEnable"))));
            }
            modes = List.copyOf(result);
        } catch (Exception e) {
            throw new IllegalStateException("读取 APK 场次配置失败", e);
        }
    }

    public List<GameMode> modes() {
        return modes;
    }

    private static int intAttr(Element element, String name) {
        return Integer.parseInt(element.getAttribute(name));
    }

    private static long longAttr(Element element, String name) {
        return Long.parseLong(element.getAttribute(name));
    }
}
