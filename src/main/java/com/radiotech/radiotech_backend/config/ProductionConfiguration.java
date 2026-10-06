package com.radiotech.radiotech_backend.config;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

/** Fail closed before serving traffic when a production release is misconfigured. */
@Configuration
@Profile("production")
public class ProductionConfiguration {
    public ProductionConfiguration(Environment environment) {
        validate(environment);
    }

    static void validate(Environment env) {
        require(env.getProperty("app.firebase.enabled", Boolean.class, true), "Firebase must be enabled");
        for (String flag : new String[] {"app.firestore.seed", "radiotech.tenant.migration.enabled"}) {
            require(!env.getProperty(flag, Boolean.class, false), "Seeding and migrations must be disabled");
        }
        require(env.getProperty("FIRESTORE_EMULATOR_HOST", "").isBlank()
                && env.getProperty("FIREBASE_AUTH_EMULATOR_HOST", "").isBlank()
                && env.getProperty("FIREBASE_STORAGE_EMULATOR_HOST", "").isBlank(), "Emulators are forbidden");
        require(!env.getProperty("app.firebase.project-id", "").isBlank(), "Firebase project is required");
        require(!env.getProperty("app.firebase.storage-bucket", "").isBlank(), "Storage bucket is required");
        require(!env.getProperty("radiotech.firebase.web-api-key", "").isBlank(), "Firebase web API key is required");
        String reportKey = env.getProperty("radiotech.reports.verification-secret", "");
        String invitationKey = env.getProperty("radiotech.tenant.invite-secret", "");
        require(reportKey.getBytes(StandardCharsets.UTF_8).length >= 32
                && invitationKey.getBytes(StandardCharsets.UTF_8).length >= 32, "Signing keys need at least 32 bytes");
        require(!reportKey.equals(invitationKey), "Use independent signing keys");
        require(httpsOrigin(env.getProperty("radiotech.reports.public-base-url", "")), "HTTPS public origin is required");
        String origins = env.getProperty("radiotech.security.cors-origins", "");
        require(!origins.isBlank() && Arrays.stream(origins.split(",")).allMatch(ProductionConfiguration::httpsOrigin),
                "Explicit HTTPS CORS origins are required");
    }

    private static boolean httpsOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && !host.equalsIgnoreCase("localhost") && !host.equals("127.0.0.1")
                    && !host.endsWith(".invalid") && !host.contains("*")
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath().isEmpty() || uri.getPath().equals("/"));
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static void require(boolean valid, String message) {
        if (!valid) throw new IllegalStateException("Production configuration: " + message);
    }
}
