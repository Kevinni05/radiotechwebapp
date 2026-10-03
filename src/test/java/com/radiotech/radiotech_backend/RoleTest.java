package com.radiotech.radiotech_backend;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.radiotech.radiotech_backend.security.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RoleTest {

    @Test
    void missingOrUnknownClaimGrantsNothing() {
        assertEquals(Role.NONE, Role.fromClaims(null));
        assertEquals(Role.NONE, Role.fromClaims(Map.of()));
        assertEquals(Role.NONE, Role.fromClaims(Map.of("role", "TECNICO")));
    }

    @Test
    void legacyAliasesAreMapped() {
        assertEquals(Role.CHIEF_EXECUTIVE, Role.fromClaims(Map.of("role", "CAPO")));
        assertEquals(Role.OPERATOR, Role.fromClaims(Map.of("role", "operatore")));
        assertEquals(Role.CHIEF_EXECUTIVE, Role.fromClaims(Map.of("admin", true)));
    }

    @Test
    void superAdminStaysDistinctFromAdmin() {
        assertEquals(Role.SUPER_ADMIN, Role.fromClaims(Map.of("role", "SUPER_ADMIN")));
    }

    @Test
    void roleClaimWinsOverLegacyAdminFlag() {
        assertEquals(Role.OPERATOR, Role.fromClaims(Map.of("role", "OPERATOR", "admin", true)));
    }
}
