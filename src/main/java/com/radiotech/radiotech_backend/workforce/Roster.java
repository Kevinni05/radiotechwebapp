package com.radiotech.radiotech_backend.workforce;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

/** Configurable rules, expressed in minutes; server dates use the tenant's IANA zone. */
public final class Roster {
    private Roster() {}
    public record Rules(@NotBlank String zone, @Min(0) @Max(2880) int minRestMinutes,
            @Min(1) @Max(31) int maxConsecutiveDays, @Min(1) @Max(10080) int maxWeeklyMinutes,
            @Min(0) @Max(240) int toleranceMinutes, @Min(1) @Max(20) int solveSeconds) {
        public Rules { ZoneId.of(zone); }
    }
    public record Template(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String id,
            @NotBlank @Size(max=80) String label, @NotBlank String start, @NotBlank String end,
            @Min(0) @Max(720) int breakMinutes, @Min(1) @Max(100) int coverage,
            @NotEmpty List<@Min(1) @Max(7) Integer> days, @Size(max=100) String site,
            @Size(max=80) String skill, boolean night) {
        public Template { LocalTime.parse(start); LocalTime.parse(end); days = List.copyOf(days); }
    }
    public record Member(@NotBlank @Size(max=128) String operatorId,
            @Min(0) @Max(44640) int minMonthlyMinutes, @Min(1) @Max(44640) int maxMonthlyMinutes,
            @Min(0) @Max(44640) int targetMonthlyMinutes, @Min(1) @Max(10080) int maxWeeklyMinutes,
            @Min(1) @Max(31) int maxConsecutiveDays, boolean allowNight,
            @NotEmpty List<@Min(1) @Max(7) Integer> days,
            List<@Pattern(regexp="\\d{4}-\\d{2}-\\d{2}") String> unavailableDates,
            List<@Size(max=40) String> allowedTemplates, List<@Size(max=80) String> skills,
            List<@Min(1) @Max(7) Integer> preferredDays) {
        public Member {
            days = List.copyOf(days); unavailableDates = copy(unavailableDates); allowedTemplates = copy(allowedTemplates);
            skills = copy(skills); preferredDays = copy(preferredDays);
            if(minMonthlyMinutes > maxMonthlyMinutes || targetMonthlyMinutes > maxMonthlyMinutes)
                throw new IllegalArgumentException("Limiti mensili incoerenti.");
            for(String date: unavailableDates) LocalDate.parse(date);
        }
    }
    public record Assignment(@NotBlank String operatorId, @NotBlank String templateId,
            @NotBlank String date, boolean locked) {
        public Assignment { LocalDate.parse(date); }
    }
    public record Config(@NotNull @Valid Rules rules, @NotEmpty @Size(max=8) List<@Valid Template> templates,
            @NotEmpty @Size(max=100) List<@Valid Member> members, @Size(max=3100) List<@Valid Assignment> assignments,
            @Min(0) long expectedVersion, @Size(max=1000) String notes) {
        public Config { templates = List.copyOf(templates); members = List.copyOf(members); assignments = copy(assignments); }
    }
    public record Version(@Min(0) long expectedVersion) {}
    public record Slot(String operatorId, String uid, String name, String templateId, String label, String date,
            String startAt, String endAt, int breakMinutes, long workMinutes, String site, boolean night, boolean locked) {}
    public record Result(String status, List<Assignment> assignments, List<String> diagnostics, double elapsedSeconds) {}
    public static <T> List<T> copy(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
    public static Slot slot(Assignment a, Template t, Rules r, String uid, String name) {
        LocalDate date = LocalDate.parse(a.date()); ZoneId zone = ZoneId.of(r.zone());
        LocalTime start = LocalTime.parse(t.start()), end = LocalTime.parse(t.end());
        ZonedDateTime from = date.atTime(start).atZone(zone);
        ZonedDateTime to = date.plusDays(!end.isAfter(start) ? 1 : 0).atTime(end).atZone(zone);
        if (!from.toLocalTime().equals(start) || !to.toLocalTime().equals(end)) throw new IllegalArgumentException("Orario inesistente nel cambio ora: " + date + " / " + t.label());
        long duration = Duration.between(from, to).toMinutes();
        if(duration <= t.breakMinutes() || duration > 1440)
            throw new IllegalArgumentException("Durata o pausa non valida nel turno " + t.label() + " del " + date);
        return new Slot(a.operatorId(), uid, name, t.id(), t.label(), a.date(), from.toInstant().toString(),
                to.toInstant().toString(), t.breakMinutes(), duration-t.breakMinutes(), Objects.toString(t.site(), ""), t.night(), a.locked());
    }
}
