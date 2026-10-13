package com.radiotech.radiotech_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class IdentityGatewayTest {
    @Test void everyPrivilegedRoleAndLegacyAliasRequiresTheAuthority() {
        for (String role : new String[] {"CAPO", "CHIEF_EXECUTIVE", "ADMIN", "SUPER_ADMIN", "NETWORK_MANAGER", "ENGINEER", "capo"})
            assertTrue(IdentityGateway.high(Map.of("role", role)), role);
        assertTrue(IdentityGateway.high(Map.of("admin", true)));
        for (String role : new String[] {"OPERATOR", "OPERATORE", "VIEWER", "CUSTOMER", "UNKNOWN"})
            assertFalse(IdentityGateway.high(Map.of("role", role)), role);
    }
    @Test void realProjectCannotDisableIdentityIsolation() {
        var gateway = new IdentityGateway();
        ReflectionTestUtils.setField(gateway, "project", "gestionale-radio");
        ReflectionTestUtils.setField(gateway, "required", false);
        assertThrows(IllegalStateException.class, gateway::initialize);
    }
    @Test void authorityUrlCannotContainCredentialsRoutesOrRedirectParameters() {
        for (String origin : new String[] {"http://access.example.org", "https://user:pass@access.example.org", "https://access.example.org/path", "https://access.example.org?redirect=attacker", "https://access.example.org#fragment"}) {
            var gateway = new IdentityGateway();
            ReflectionTestUtils.setField(gateway, "origin", origin);
            assertThrows(IllegalStateException.class, gateway::initialize, origin);
        }
    }
}
