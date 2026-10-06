package com.radiotech.radiotech_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component("reportVerification")
public class ReportVerificationHealthIndicator implements HealthIndicator {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.radiotech.radiotech_backend.service.LocalAttachmentService localFiles;
    private final String secret;
    private final String publicBaseUrl;
    private final String storageBucket;

    public ReportVerificationHealthIndicator(
            @Value("${radiotech.reports.verification-secret:}") String secret,
            @Value("${radiotech.reports.public-base-url:}") String publicBaseUrl,
            @Value("${app.firebase.storage-bucket:}") String storageBucket) {
        this.secret = secret;
        this.publicBaseUrl = publicBaseUrl;
        this.storageBucket = storageBucket;
    }

    @Override
    public Health health() {
        if (secret == null || secret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < 32) {
            return Health.down().withDetail("dependency", "report-verification-key").build();
        }
        if ((localFiles==null||!localFiles.enabled()) && (storageBucket == null || !storageBucket.matches("[A-Za-z0-9][A-Za-z0-9.-]{1,220}[A-Za-z0-9]"))) {
            return Health.down().withDetail("dependency", "firebase-storage-bucket").build();
        }
        try {
            URI uri = URI.create(publicBaseUrl == null ? "" : publicBaseUrl.trim());
            boolean secure = "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null;
            boolean local = "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
            return secure || local ? Health.up().build()
                    : Health.down().withDetail("dependency", "report-verification-public-url").build();
        } catch (IllegalArgumentException invalidUrl) {
            return Health.down().withDetail("dependency", "report-verification-public-url").build();
        }
    }
}
