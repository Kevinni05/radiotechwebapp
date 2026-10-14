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
    @Test void deviceSessionResponseMustMatchTheAuthenticatedUserTenantAndInstallation() {
        var response = new java.util.HashMap<String, Object>(Map.of("uid", "u", "tenantId", "t", "deviceId", "d", "customToken", "signed"));
        assertEquals("signed", IdentityGateway.verifiedDeviceSessionToken(response, "u", "t", "d"));
        for (String key : new String[] {"uid", "tenantId", "deviceId", "customToken"}) {
            var invalid = new java.util.HashMap<String, Object>(response);
            invalid.put(key, key.equals("customToken") ? " " : "other");
            assertThrows(SecurityException.class, () -> IdentityGateway.verifiedDeviceSessionToken(invalid, "u", "t", "d"), key);
            invalid.remove(key);
            assertThrows(SecurityException.class, () -> IdentityGateway.verifiedDeviceSessionToken(invalid, "u", "t", "d"), key);
        }
    }
    @Test void aDeviceMfaProofIsLimitedToLowRolesAndAValidBoundDevice() {
        for (String role : new String[] {"OPERATOR", "OPERATORE", "VIEWER", "CUSTOMER"})
            assertTrue(IdentityGateway.lowDeviceMfa(Map.of("role", role, "radioDeviceId", "a".repeat(64), "radioMfaVerified", true)));
        for (String role : new String[] {"ADMIN", "SUPER_ADMIN", "CAPO", "CHIEF_EXECUTIVE", "NETWORK_MANAGER", "ENGINEER", "NONE"})
            assertFalse(IdentityGateway.lowDeviceMfa(Map.of("role", role, "radioDeviceId", "a".repeat(64), "radioMfaVerified", true)));
        assertFalse(IdentityGateway.lowDeviceMfa(Map.of("role", "OPERATOR", "admin", true, "radioDeviceId", "a".repeat(64), "radioMfaVerified", true)));
        assertFalse(IdentityGateway.lowDeviceMfa(Map.of("role", "OPERATOR", "radioDeviceId", "invalid", "radioMfaVerified", true)));
        assertFalse(IdentityGateway.lowDeviceMfa(Map.of("role", "OPERATOR", "radioDeviceId", "a".repeat(64), "radioMfaVerified", false)));
    }
}
