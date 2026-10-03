package com.radiotech.radiotech_backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportVerificationHealthIndicatorTest {
    @Test
    void readinessRequiresLongSecretAndHttpsPublicOrigin() {
        assertEquals("DOWN", new ReportVerificationHealthIndicator("short", "https://reports.example.test", "radio.appspot.com")
                .health().getStatus().getCode());
        assertEquals("DOWN", new ReportVerificationHealthIndicator("0123456789abcdef0123456789abcdef", "http://reports.example.test", "radio.appspot.com")
                .health().getStatus().getCode());
        assertEquals("DOWN", new ReportVerificationHealthIndicator("0123456789abcdef0123456789abcdef", "https://reports.example.test", "")
                .health().getStatus().getCode());
        assertEquals("UP", new ReportVerificationHealthIndicator("0123456789abcdef0123456789abcdef", "https://reports.example.test", "radio.appspot.com")
                .health().getStatus().getCode());
    }
}
