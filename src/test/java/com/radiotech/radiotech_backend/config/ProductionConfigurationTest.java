package com.radiotech.radiotech_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class ProductionConfigurationTest {
    private MockEnvironment valid() {
        return new MockEnvironment()
                .withProperty("app.firebase.project-id", "gestionale-radio")
                .withProperty("app.firebase.storage-bucket", "gestionale-radio.firebasestorage.app")
                .withProperty("radiotech.firebase.web-api-key", "public-web-api-key")
                .withProperty("radiotech.reports.verification-secret", "r".repeat(40))
                .withProperty("radiotech.tenant.invite-secret", "i".repeat(40))
                .withProperty("radiotech.reports.public-base-url", "https://radio.example.org")
                .withProperty("radiotech.security.cors-origins", "https://radio.example.org");
    }
    @Test void acceptsCompleteProductionConfiguration() {
        assertDoesNotThrow(() -> ProductionConfiguration.validate(valid()));
    }
    @Test void refusesDevelopmentFlagsAndEndpoints() {
        for (String flag : new String[] {"app.firestore.seed", "radiotech.tenant.migration.enabled"}) {
            assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validate(valid().withProperty(flag, "true")));
        }
        assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validate(valid().withProperty("FIRESTORE_EMULATOR_HOST", "localhost:8080")));
        assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validate(valid().withProperty("app.firebase.enabled", "false")));
        for (String origin : new String[] {"http://radio.example.org", "https://localhost", "https://reports.example.invalid", "https://radio.example.org/path", "https://radio.example.org?key=value", "*"}) {
            assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validate(valid().withProperty("radiotech.security.cors-origins", origin)));
        }
    }
    @Test void refusesMissingOrReusedSecrets() {
        assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validate(valid().withProperty("radiotech.reports.verification-secret", "short")));
        assertThrows(IllegalStateException.class, () -> ProductionConfiguration.validate(valid().withProperty("radiotech.tenant.invite-secret", "r".repeat(40))));
    }
}
