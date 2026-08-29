package cc.superbaby.happy4j.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {
    private final SessionOwnershipInterceptor ownership;

    public WebMvcConfiguration(SessionOwnershipInterceptor ownership) {
        this.ownership = ownership;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ownership).addPathPatterns("/api/sessions/{id}/**");
    }
}
