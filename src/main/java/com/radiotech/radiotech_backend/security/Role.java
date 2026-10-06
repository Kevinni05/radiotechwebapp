package com.radiotech.radiotech_backend.security;

import java.util.Locale;
import java.util.Map;

/**
 * Modello unico dei ruoli applicativi.
 *
 * Prima i ruoli erano normalizzati nel filtro Firebase, elencati a mano in
 * SecurityConfig e ripetuti in CapoService. Ora la traduzione claim -> ruolo
 * avviene solo qui. Un claim assente o sconosciuto NON concede piu' alcun
 * privilegio (Role.NONE).
 */
public enum Role {
    SUPER_ADMIN, ADMIN, CHIEF_EXECUTIVE, NETWORK_MANAGER, ENGINEER, OPERATOR, VIEWER, CUSTOMER, NONE;

    /** Ruoli che possono usare le API della Control Room. */
    public static final String[] MANAGERS = { "SUPER_ADMIN", "ADMIN", "CHIEF_EXECUTIVE", "NETWORK_MANAGER" };

    /**
     * Manager e viewer ammessi alle route Control Room; method checks govern
     * writes.
     */
    public static final String[] CONTROL_ROOM_API = {
            "SUPER_ADMIN", "ADMIN", "CHIEF_EXECUTIVE", "NETWORK_MANAGER", "VIEWER"
    };

    /** Ruoli che possono gestire gli account Firebase degli operatori. */
    public static final String[] ACCOUNT_ADMINS = { "SUPER_ADMIN", "ADMIN", "CHIEF_EXECUTIVE" };

    /** Ruoli ammessi sulle API mobile /api/operator/**. */
    public static final String[] OPERATOR_API = { "OPERATOR", "SUPER_ADMIN", "ADMIN" };

    /** Ruoli abilitati alla consultazione e gestione degli incident operativi. */
    public static final String[] INCIDENT_API = {
            "SUPER_ADMIN", "ADMIN", "CHIEF_EXECUTIVE", "NETWORK_MANAGER", "OPERATOR"
    };

    public static Role parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return switch (raw.trim().toUpperCase(Locale.ROOT)) {
            case "SUPER_ADMIN" -> SUPER_ADMIN;
            case "ADMIN" -> ADMIN;
            case "CAPO", "CHIEF_EXECUTIVE" -> CHIEF_EXECUTIVE;
            case "NETWORK_MANAGER" -> NETWORK_MANAGER;
            case "ENGINEER" -> ENGINEER;
            case "OPERATOR", "OPERATORE" -> OPERATOR;
            case "VIEWER" -> VIEWER;
            case "CUSTOMER" -> CUSTOMER;
            default -> NONE;
        };
    }

    public static Role fromClaims(Map<String, Object> claims) {
        if (claims == null) {
            return NONE;
        }
        Object raw = claims.get("role");
        Role role = parse(raw == null ? null : String.valueOf(raw));
        if (role != NONE) {
            return role;
        }
        // Compatibilita' con il vecchio claim booleano impostato per il CAPO.
        return Boolean.TRUE.equals(claims.get("admin")) ? CHIEF_EXECUTIVE : NONE;
    }

    public String authority() {
        return "ROLE_" + name();
    }
}
