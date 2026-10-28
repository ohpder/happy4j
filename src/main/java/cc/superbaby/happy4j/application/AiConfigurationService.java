package cc.superbaby.happy4j.application;

import cc.superbaby.happy4j.application.port.GameModeCatalogPort;
import cc.superbaby.happy4j.domain.AiConfiguration;
import cc.superbaby.happy4j.domain.GameMode;
import cc.superbaby.happy4j.infrastructure.persistence.entity.AiConfigurationEntity;
import cc.superbaby.happy4j.infrastructure.persistence.repository.AiConfigurationRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiConfigurationService {
    private static final long MAX_BEAN_LIMIT = 10_000_000_000L;
    private final AiConfigurationRepository configurations;
    private final GameModeCatalogPort gameModes;
    private final SecretCipherService secrets;

    public AiConfigurationService(AiConfigurationRepository configurations, GameModeCatalogPort gameModes,
                                  SecretCipherService secrets) {
        this.configurations = configurations;
        this.gameModes = gameModes;
        this.secrets = secrets;
    }

    @Transactional(readOnly = true)
    public AiConfiguration get(UUID userId) {
        return configurations.findById(userId).map(AiConfigurationEntity::toDomain)
                .orElseGet(AiConfiguration::defaults);
    }

    @Transactional
    public AiConfiguration save(UUID userId, Update request) {
        int sceneId = validateScene(request.sceneId());
        String hostingMode = normalizeHostingMode(request.hostingMode());
        long stopAt = validateLimit(request.stopAtBeans(), "目标欢乐豆");
        long stopBelow = validateLimit(request.stopBelowBeans(), "最低欢乐豆");
        if (stopAt > 0 && stopBelow > 0 && stopBelow >= stopAt)
            throw new IllegalArgumentException("最低欢乐豆必须小于目标欢乐豆");
        String engine = normalizeEngine(request.inferenceEngine());
        String baseUrl = normalizeUrl(request.apiBaseUrl());
        String model = normalizeModel(baseUrl, request.apiModel());
        AiConfigurationEntity entity = configurations.findById(userId)
                .orElseGet(() -> new AiConfigurationEntity(userId));
        if (request.clearApiKey()) entity.apiKey(null, null);
        else if (request.apiKey() != null && !request.apiKey().isBlank()) {
            String apiKey = request.apiKey().trim();
            if (apiKey.length() > 4096) throw new IllegalArgumentException("API Key 长度超过限制");
            var encrypted = secrets.encrypt(userId, "openai-api-key", apiKey);
            entity.apiKey(encrypted.encrypted(), encrypted.iv());
        }
        if (request.enabled() && "OPENAI".equals(engine)) validateProvider(baseUrl, model);
        entity.apply(sceneId, hostingMode, request.autoChangeTable(), request.autoClaimBeans(),
                stopAt, stopBelow, engine, baseUrl, model, request.enabled());
        return configurations.save(entity).toDomain();
    }

    @Transactional(readOnly = true)
    public List<ServerConfiguration> serverConfigurations() {
        return configurations.findAllByEnabledTrueAndHostingMode("SERVER").stream()
                .map(entity -> new ServerConfiguration(entity.getUserId(), entity.toDomain()))
                .toList();
    }

    @Transactional
    public AiConfiguration disable(UUID userId) {
        AiConfigurationEntity entity = configurations.findById(userId).orElse(null);
        if (entity == null) return AiConfiguration.defaults();
        entity.enabled(false);
        return configurations.save(entity).toDomain();
    }

    @Transactional
    public AiConfiguration requestStop(UUID userId, StopMode mode) {
        AiConfigurationEntity entity = configurations.findById(userId).orElse(null);
        if (entity == null) return AiConfiguration.defaults();
        if (mode == StopMode.AFTER_ROUND && entity.enabled()) entity.stopAfterRound(true);
        else entity.enabled(false);
        return configurations.save(entity).toDomain();
    }

    @Transactional(readOnly = true)
    public ProviderConfiguration provider(UUID userId) {
        AiConfigurationEntity entity = configurations.findById(userId)
                .orElseThrow(() -> new IllegalStateException("请先保存AI配置"));
        if (!"OPENAI".equals(normalizeEngine(entity.getInferenceEngine())))
            throw new IllegalStateException("当前未启用OpenAI兼容大模型");
        String baseUrl = normalizeUrl(entity.getApiBaseUrl());
        String model = normalizeModel(baseUrl, entity.getApiModel());
        validateProvider(baseUrl, model);
        String apiKey = entity.hasApiKey()
                ? secrets.decrypt(userId, "openai-api-key", entity.getEncryptedApiKey(), entity.getApiKeyIv()) : "";
        return new ProviderConfiguration(URI.create(chatCompletionsUrl(baseUrl)), model, apiKey);
    }

    private int validateScene(int sceneId) {
        GameMode mode = gameModes.modes().stream()
                .filter(candidate -> candidate.sceneId() == sceneId && candidate.enabled())
                .findFirst().orElseThrow(() -> new IllegalArgumentException("请选择当前开放的游戏模式"));
        if (!"CLASSIC".equalsIgnoreCase(mode.family()))
            throw new IllegalArgumentException("DouZero FullAuto ResNet 当前仅支持经典斗地主模式");
        return mode.sceneId();
    }

    private static long validateLimit(long value, String label) {
        if (value < 0 || value > MAX_BEAN_LIMIT)
            throw new IllegalArgumentException(label + "范围应为 0 到 " + MAX_BEAN_LIMIT);
        return value;
    }

    private static String normalizeEngine(String value) {
        String engine = value == null ? "DOUZERO" : value.trim().toUpperCase(Locale.ROOT);
        if (!engine.equals("DOUZERO") && !engine.equals("OPENAI"))
            throw new IllegalArgumentException("未知AI推理引擎");
        return engine;
    }

    private static String normalizeHostingMode(String value) {
        String mode = value == null ? "BROWSER" : value.trim().toUpperCase(Locale.ROOT);
        if (!mode.equals("BROWSER") && !mode.equals("SERVER"))
            throw new IllegalArgumentException("未知AI托管方式");
        return mode;
    }

    private static String normalizeUrl(String value) {
        String url = trim(value, 1024);
        if (url.isBlank()) return "";
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("大模型URL格式错误", failure);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null
                || uri.getUserInfo() != null)
            throw new IllegalArgumentException("大模型URL必须是有效的HTTP或HTTPS地址");
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static void validateProvider(String baseUrl, String model) {
        if (baseUrl.isBlank()) throw new IllegalArgumentException("请填写OpenAI兼容URL");
        if (model.isBlank()) throw new IllegalArgumentException("请填写大模型名称");
    }

    private static String normalizeModel(String baseUrl, String value) {
        String model = trim(value, 128);
        if (baseUrl.isBlank() || model.isBlank()) return model;
        String host = URI.create(baseUrl).getHost();
        if (host != null && (host.equalsIgnoreCase("open.bigmodel.cn")
                || host.toLowerCase(Locale.ROOT).endsWith(".bigmodel.cn"))
                && model.regionMatches(true, 0, "glm-", 0, 4))
            return model.toLowerCase(Locale.ROOT);
        return model;
    }

    private static String chatCompletionsUrl(String baseUrl) {
        if (baseUrl.endsWith("/chat/completions")) return baseUrl;
        String path = URI.create(baseUrl).getPath();
        return baseUrl + (path == null || path.isBlank() || path.equals("/")
                ? "/v1/chat/completions" : "/chat/completions");
    }

    private static String trim(String value, int max) {
        String result = value == null ? "" : value.trim();
        if (result.length() > max) throw new IllegalArgumentException("配置内容长度超过限制");
        return result;
    }

    public record Update(int sceneId, String hostingMode, boolean autoChangeTable, boolean autoClaimBeans,
                         long stopAtBeans, long stopBelowBeans, String inferenceEngine,
                         String apiBaseUrl, String apiModel, String apiKey,
                         boolean clearApiKey, boolean enabled) {
        public Update(int sceneId, boolean autoChangeTable, boolean autoClaimBeans,
                      long stopAtBeans, long stopBelowBeans, String inferenceEngine,
                      String apiBaseUrl, String apiModel, String apiKey,
                      boolean clearApiKey, boolean enabled) {
            this(sceneId, "BROWSER", autoChangeTable, autoClaimBeans, stopAtBeans, stopBelowBeans,
                    inferenceEngine, apiBaseUrl, apiModel, apiKey, clearApiKey, enabled);
        }
    }

    public record ProviderConfiguration(URI endpoint, String model, String apiKey) {
    }

    public enum StopMode {
        IMMEDIATE,
        AFTER_ROUND
    }

    public record ServerConfiguration(UUID userId, AiConfiguration configuration) {
    }
}
