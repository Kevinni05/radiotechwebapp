package com.radiotech.radiotech_backend.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EngineerRoleContractTest {
    @Test
    void engineerUsesExistingNetworkManagementCapabilitiesWithoutAccountAdministration() {
        for (Permission permission : Permission.values()) {
            assertEquals(TenantAccessPolicy.canAccess(Role.NETWORK_MANAGER, permission),
                    TenantAccessPolicy.canAccess(Role.ENGINEER, permission), permission.name());
        }
        assertTrue(Arrays.asList(Role.MANAGERS).contains("ENGINEER"));
        assertTrue(Arrays.asList(Role.CONTROL_ROOM_API).contains("ENGINEER"));
        assertTrue(Arrays.asList(Role.INCIDENT_API).contains("ENGINEER"));
        assertFalse(Arrays.asList(Role.ACCOUNT_ADMINS).contains("ENGINEER"));
        assertFalse(Arrays.asList(Role.OPERATOR_API).contains("ENGINEER"));
        assertFalse(TenantAccessPolicy.canAccessTenant("tenant-a", "tenant-b"));
    }
}
