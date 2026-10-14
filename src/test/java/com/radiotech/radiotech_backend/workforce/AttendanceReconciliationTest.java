package com.radiotech.radiotech_backend.workforce;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class AttendanceReconciliationTest {
    Map<String,Object> slot(String uid,String start,String end) {return Map.of("uid",uid,"name",uid,"date",start.substring(0,10),"startAt",start,"endAt",end,"workMinutes",480);}
    Map<String,Object> session(String uid,String start,String end) {return new LinkedHashMap<>(Map.of("id",uid,"uid",uid,"name",uid,"startedAt",start,"endedAt",end,"workMinutes",470));}
    @Test void matchesEachSessionOnceAndDetectsLateExitAndMissingEntry() {
        var slots=List.of(slot("a","2026-10-14T06:00:00Z","2026-10-14T14:00:00Z"),slot("b","2026-10-14T06:00:00Z","2026-10-14T14:00:00Z"));
        var rows=List.of(session("a","2026-10-14T06:18:00Z","2026-10-14T13:40:00Z"));
        var result=AttendanceService.reconcile(slots,rows,10,Instant.parse("2026-10-14T15:00:00Z"));
        assertTrue(result.stream().anyMatch(a->a.get("type").equals("LATE")));assertTrue(result.stream().anyMatch(a->a.get("type").equals("EARLY_EXIT")));
        assertEquals(1,result.stream().filter(a->a.get("type").equals("MISSING_ENTRY")).count());
    }
    @Test void approvedCorrectionIsComparedWhileOriginalsRemainUnchanged() {
        var row=session("a","2026-10-14T06:18:00Z","2026-10-14T13:40:00Z");
        row.put("approved",Map.of("startedAt","2026-10-14T06:00:00Z","endedAt","2026-10-14T14:00:00Z","workMinutes",480));
        assertTrue(AttendanceService.reconcile(List.of(slot("a","2026-10-14T06:00:00Z","2026-10-14T14:00:00Z")),List.of(row),10,Instant.parse("2026-10-14T15:00:00Z")).isEmpty());
        assertEquals("2026-10-14T06:18:00Z",row.get("startedAt"));
    }
    @Test void completedPreviousMonthSessionIsNotMarkedUnplannedInTheNewMonth() {
        var prior=session("a","2026-09-30T20:00:00Z","2026-10-01T04:00:00Z");prior.put("boundarySession",true);
        assertTrue(AttendanceService.reconcile(List.of(),List.of(prior),10,Instant.parse("2026-10-01T10:00:00Z")).isEmpty());
    }
    @Test void futureShiftDoesNotCreateAnAbsenceAndOvernightSessionMatches() {
        var result=AttendanceService.reconcile(List.of(slot("a","2026-10-14T20:00:00Z","2026-10-15T04:00:00Z")),List.of(),10,Instant.parse("2026-10-14T19:00:00Z"));assertTrue(result.isEmpty());
        result=AttendanceService.reconcile(List.of(slot("a","2026-10-14T20:00:00Z","2026-10-15T04:00:00Z")),List.of(session("a","2026-10-14T20:00:00Z","2026-10-15T04:00:00Z")),10,Instant.parse("2026-10-15T08:00:00Z"));assertTrue(result.isEmpty());
    }
}
