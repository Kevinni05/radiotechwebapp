package com.radiotech.radiotech_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebConfigCorsTest {

    @Test
    void configuredOriginsCanSubmitIdempotentRequests() {
        WebConfig webConfig = new WebConfig();
        ReflectionTestUtils.setField(webConfig, "allowedOrigins", "https://control.example");
        ExposedCorsRegistry registry = new ExposedCorsRegistry();

        webConfig.addCorsMappings(registry);

        CorsConfiguration cors = registry.configurations().get("/api/**");
        assertEquals("https://control.example", cors.checkOrigin("https://control.example"));
        assertTrue(cors.checkHeaders(List.of("Authorization", "Content-Type", "X-Tenant-Id", "Idempotency-Key"))
                .contains("Idempotency-Key"));
        assertTrue(cors.checkHeaders(List.of("X-Tenant-Id")).contains("X-Tenant-Id"));
    }

    private static class ExposedCorsRegistry extends CorsRegistry {
        Map<String, CorsConfiguration> configurations() {
            return getCorsConfigurations();
        }
    }
}