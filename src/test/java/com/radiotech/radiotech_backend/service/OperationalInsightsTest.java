package com.radiotech.radiotech_backend.service;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OperationalInsightsTest {
    private final Instant now = Instant.parse("2026-10-03T12:00:00Z");
    @Test void scoresHaveEvidenceAndNeverInventFailureProbabilityOrHistory() {
        var result = OperationalInsightsService.calculate(List.of(Map.of("id", "a", "status", "OFFLINE", "specs", Map.of("ROS", 2.5, "Temperatura", 70))), List.of(), List.of(), now);
        Map<?, ?> risk = (Map<?, ?>)((List<?>)result.get("risks")).getFirst();
        assertEquals(95, risk.get("score")); assertEquals("CRITICAL", risk.get("level")); assertEquals(3, ((List<?>)risk.get("reasons")).size());
        assertNull(risk.get("estimatedMaintenanceAt")); assertFalse(risk.containsKey("failureProbability"));
    }
    @Test void maintenanceEstimateUsesApprovedCompletedDatesAndExcludesFutureOrUnapprovedEvents() {
        List<Map<String, Object>> reports = new ArrayList<>();
        for (String date : List.of("2026-09-01", "2026-09-11", "2026-09-21", "2027-01-01")) reports.add(Map.of("antennaId", "a", "status", "APPROVED", "completedAt", date + "T12:00:00Z"));
        reports.add(Map.of("antennaId", "a", "status", "REJECTED", "completedAt", "2026-09-30T12:00:00Z"));
        var result = OperationalInsightsService.calculate(List.of(Map.of("id", "a", "status", "ATTIVA")), List.of(Map.of("antennaId", "a", "status", "REPORT_SUBMITTED", "dueAt", "2026-01-01T00:00:00Z")), reports, now);
        Map<?, ?> risk = (Map<?, ?>)((List<?>)result.get("risks")).getFirst();
        assertEquals(10L, risk.get("medianIntervalDays")); assertEquals("2026-10-01T12:00:00Z", risk.get("estimatedMaintenanceAt"));
        assertEquals(3, risk.get("historyCount")); assertEquals(0L, result.get("overdueTasks"));
    }
    @Test void evenHistoricalIntervalsUseTheirAverageMedian() {
        List<Map<String, Object>> reports = new ArrayList<>();
        for (String date : List.of("2026-09-01", "2026-09-11", "2026-10-01"))
            reports.add(Map.of("antennaId", "a", "status", "APPROVED", "completedAt", date + "T12:00:00Z"));
        var result = OperationalInsightsService.calculate(List.of(Map.of("id", "a")), List.of(), reports, now);
        Map<?, ?> risk = (Map<?, ?>)((List<?>)result.get("risks")).getFirst();
        assertEquals(15L, risk.get("medianIntervalDays"));
        assertEquals("2026-10-16T12:00:00Z", risk.get("estimatedMaintenanceAt"));
    }
    @Test void missingOrMalformedTelemetryIsExplicitlyLimited() {
        var result = OperationalInsightsService.calculate(List.of(Map.of("id", "a", "specs", Map.of("ros", Double.NaN))), List.of(Map.of("status", "ASSIGNED", "dueAt", "bad-date")), List.of(), now);
        Map<?, ?> risk = (Map<?, ?>)((List<?>)result.get("risks")).getFirst();
        assertEquals("LIMITED", risk.get("dataQuality")); assertNull(risk.get("estimatedMaintenanceAt")); assertEquals(0L, result.get("overdueTasks"));
    }
    @Test void pausesAreSuggestionsAndStaleShiftIsNotPresentedAsFresh() {
        var shift = WorkforceService.decorate(Map.of("status", "ACTIVE", "activeMinutes", 10, "breakMinutes", 20,
                "lastTransitionAt", "2026-10-03T10:00:00Z", "lastRestAt", "2026-10-03T10:00:00Z", "updatedAt", "2026-10-02T10:00:00Z"), now);
        assertEquals(130L, shift.get("workMinutes")); assertEquals(20L, shift.get("restMinutes")); assertEquals(true, shift.get("pauseSuggested")); assertEquals(true, shift.get("stale"));
    }
}
