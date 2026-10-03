package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.service.AuditService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditServiceTest {

    @Test
    void recentRequiresTenantScope() {
        AuditService auditService = new AuditService();

        assertThrows(IllegalArgumentException.class, () -> auditService.recent(50, " "));
    }
}