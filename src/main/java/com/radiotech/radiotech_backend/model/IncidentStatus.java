package com.radiotech.radiotech_backend.model;

import java.util.Locale;

public enum IncidentStatus {
    DETECTED,
    ACKNOWLEDGED,
    ASSIGNED,
    INVESTIGATING,
    MITIGATED,
    RESOLVED,
    POST_MORTEM,
    CLOSED;

    public static IncidentStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public boolean canTransitionTo(IncidentStatus target) {
        if (target == null) {
            return false;
        }
        return switch (this) {
            case DETECTED -> target == ACKNOWLEDGED;
            case ACKNOWLEDGED -> target == ASSIGNED;
            case ASSIGNED -> target == INVESTIGATING;
            case INVESTIGATING -> target == MITIGATED;
            case MITIGATED -> target == RESOLVED;
            case RESOLVED -> target == POST_MORTEM || target == CLOSED;
            case POST_MORTEM -> target == CLOSED;
            case CLOSED -> false;
        };
    }
}
