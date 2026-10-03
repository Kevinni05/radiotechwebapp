package com.radiotech.radiotech_backend.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SlaPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-01T10:00:00Z");
    private static final Duration WARNING = Duration.ofMinutes(60);

    @Test
    void classifiesResolutionDeadline() {
        assertEquals(SlaPolicy.Status.UNSCHEDULED, SlaPolicy.evaluate(null, NOW, WARNING));
        assertEquals(SlaPolicy.Status.ON_TRACK,
                SlaPolicy.evaluate("2026-10-01T14:00:00Z", NOW, WARNING));
        assertEquals(SlaPolicy.Status.AT_RISK,
                SlaPolicy.evaluate("2026-10-01T10:45:00Z", NOW, WARNING));
        assertEquals(SlaPolicy.Status.BREACHED,
                SlaPolicy.evaluate("2026-10-01T09:59:59Z", NOW, WARNING));
    }

    @Test
    void invalidDeadlineIsNotCountedAsAValidSla() {
        assertEquals(SlaPolicy.Status.INVALID,
                SlaPolicy.evaluate("not-an-instant", NOW, WARNING));
        assertThrows(IllegalArgumentException.class,
                () -> SlaPolicy.evaluate("2026-10-01T11:00:00Z", NOW, Duration.ofMinutes(-1)));
    }
}