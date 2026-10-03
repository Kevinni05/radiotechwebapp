package com.radiotech.radiotech_backend.service;

import java.time.Duration;
import java.time.Instant;

public final class SlaPolicy {

    public enum Status {
        UNSCHEDULED,
        ON_TRACK,
        AT_RISK,
        BREACHED,
        INVALID
    }

    private SlaPolicy() {
    }

    public static Status evaluate(String dueAt, Instant now, Duration warningWindow) {
        if (dueAt == null || dueAt.isBlank()) {
            return Status.UNSCHEDULED;
        }
        if (now == null || warningWindow == null || warningWindow.isNegative()) {
            throw new IllegalArgumentException("Parametri SLA non validi.");
        }

        final Instant deadline;
        try {
            deadline = Instant.parse(dueAt.trim());
        } catch (Exception exception) {
            return Status.INVALID;
        }

        if (deadline.isBefore(now)) {
            return Status.BREACHED;
        }
        return deadline.compareTo(now.plus(warningWindow)) <= 0
                ? Status.AT_RISK
                : Status.ON_TRACK;
    }
}