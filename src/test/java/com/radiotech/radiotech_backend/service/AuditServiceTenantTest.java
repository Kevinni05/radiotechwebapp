package com.radiotech.radiotech_backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditServiceTenantTest {

    @Test
    void auditWritesRequireANonBlankTenant() {
        assertThrows(IllegalArgumentException.class, () -> AuditService.requireTenantId(null));
        assertThrows(IllegalArgumentException.class, () -> AuditService.requireTenantId(" "));
        assertEquals("tenant-a", AuditService.requireTenantId(" tenant-a "));
    }
}