package com.radiotech.radiotech_backend.model;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public enum TaskStatus {
    CREATED,
    ASSIGNED,
    ACCEPTED,
    EN_ROUTE,
    CHECKED_IN,
    IN_PROGRESS,
    WAITING,
    COMPLETED,
    REPORT_SUBMITTED,
    APPROVED,
    CLOSED,
    CANCELLED;

    private static final Set<TaskStatus> TERMINAL_STATUSES = EnumSet.of(CLOSED, CANCELLED);

    public static TaskStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String value = raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_');

        return switch (value) {
            case "CREATED", "NEW" -> CREATED;
            case "ASSIGNED", "PENDENTE" -> ASSIGNED;
            case "ACCEPTED", "ACCETTATO" -> ACCEPTED;
            case "EN_ROUTE", "IN_TRANSITO", "ROUTE" -> EN_ROUTE;
            case "CHECKED_IN", "CHECK_IN", "CHECKEDIN" -> CHECKED_IN;
            case "IN_PROGRESS", "IN_CORSO", "IN_LAVORAZIONE" -> IN_PROGRESS;
            case "WAITING", "IN_ATTESA" -> WAITING;
            case "COMPLETED", "COMPLETATO" -> COMPLETED;
            case "REPORT_SUBMITTED", "REPORT", "SUBMITTED" -> REPORT_SUBMITTED;
            case "APPROVED", "APPROVATO" -> APPROVED;
            case "CLOSED", "CHIUSO" -> CLOSED;
            case "CANCELLED", "ANNULLATO", "CANCELED" -> CANCELLED;
            default -> null;
        };
    }

    public boolean canTransitionTo(TaskStatus next) {
        if (next == null) {
            return false;
        }
        if (this == next) {
            return true;
        }
        if (TERMINAL_STATUSES.contains(this)) {
            return false;
        }

        return switch (this) {
            case CREATED -> Set.of(ASSIGNED, CANCELLED).contains(next);
            case ASSIGNED -> Set.of(ACCEPTED, CANCELLED).contains(next);
            case ACCEPTED -> Set.of(EN_ROUTE, CANCELLED).contains(next);
            case EN_ROUTE -> Set.of(CHECKED_IN, CANCELLED).contains(next);
            case CHECKED_IN -> Set.of(IN_PROGRESS, CANCELLED).contains(next);
            case IN_PROGRESS -> Set.of(WAITING, COMPLETED, CANCELLED).contains(next);
            case WAITING -> Set.of(IN_PROGRESS, COMPLETED, CANCELLED).contains(next);
            case COMPLETED -> Set.of(REPORT_SUBMITTED, APPROVED, CANCELLED).contains(next);
            case REPORT_SUBMITTED -> Set.of(APPROVED, CLOSED, CANCELLED).contains(next);
            case APPROVED -> Set.of(CLOSED, CANCELLED).contains(next);
            case CLOSED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return TERMINAL_STATUSES.contains(this)
                || this == CLOSED || this == CANCELLED;
    }

    public boolean requiresExecutionPermission() {
        return switch (this) {
            case CREATED, ASSIGNED, ACCEPTED, EN_ROUTE, CHECKED_IN, IN_PROGRESS, WAITING -> true;
            default -> false;
        };
    }

    public boolean requiresCompletionPermission() {
        return this == COMPLETED || this == REPORT_SUBMITTED;
    }

    public boolean requiresApprovalPermission() {
        return this == APPROVED || this == CLOSED;
    }
}
