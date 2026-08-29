package cc.superbaby.happy4j.config;

import cc.superbaby.happy4j.protocol.schema.ProtocolRegistry;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(Happy4jProperties.class)
public class Happy4jConfiguration {
    @Bean
    ProtocolRegistry protocolRegistry() {
        return ProtocolRegistry.loadDefault();
    }

    @Bean
    HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Bean("persistenceExecutor")
    TaskExecutor persistenceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Aggregate events are ordered; one writer preserves event index/version order and
        // avoids concurrent optimistic updates/deadlocks on the same game_client_session row.
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2000);
        executor.setThreadNamePrefix("game-persist-");
        executor.initialize();
        return executor;
    }
}
