package com.radiotech.radiotech_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final com.radiotech.radiotech_backend.service.TenantDashboardCache dashboardCache;

    public WebConfig(com.radiotech.radiotech_backend.service.TenantDashboardCache dashboardCache) {
        this.dashboardCache = dashboardCache;
    }

    @Override
    public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public void afterCompletion(jakarta.servlet.http.HttpServletRequest request,
                    jakarta.servlet.http.HttpServletResponse response, Object handler, Exception failure) {
                // Partial writes can precede an error response: invalidate on every authenticated write attempt.
                if (java.util.Set.of("POST", "PUT", "PATCH", "DELETE").contains(request.getMethod())) {
                    Object tenant = request.getAttribute("tenantId");
                    if (tenant instanceof String value) dashboardCache.invalidate(value);
                }
            }
        }).addPathPatterns("/api/**");
    }
    @Value("${radiotech.security.cors-origins:http://localhost:8080}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Tenant-Id",
                        "Idempotency-Key")
                .exposedHeaders("Authorization", "X-Request-Id", "Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
