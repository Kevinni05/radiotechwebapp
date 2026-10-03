package com.radiotech.radiotech_backend.initializer;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantMigrationRunnerTest {

    @Test
    void migrationIsDisabledByDefault() {
        assertDoesNotThrow(() -> new TenantMigrationRunner().run());
    }

    @Test
    void migrationRequiresAValidTenantBeforeAccessingFirebase() {
        TenantMigrationRunner runner = runner(true, "invalid tenant", true, false, false);

        assertThrows(IllegalStateException.class, runner::run);
    }

    @Test
    void migrationRequiresExplicitSingleTenantConfirmation() {
        TenantMigrationRunner runner = runner(true, "tenant-a", false, false, false);

        assertThrows(IllegalStateException.class, runner::run);
    }

    @Test
    void applyRequiresReplaceDefaultConfirmation() {
        TenantMigrationRunner runner = runner(true, "tenant-a", true, false, true);

        assertThrows(IllegalStateException.class, runner::run);
    }

    private TenantMigrationRunner runner(boolean enabled, String tenantId, boolean confirmed,
            boolean replaceDefault, boolean apply) {
        TenantMigrationRunner runner = new TenantMigrationRunner();
        ReflectionTestUtils.setField(runner, "enabled", enabled);
        ReflectionTestUtils.setField(runner, "tenantId", tenantId);
        ReflectionTestUtils.setField(runner, "singleTenantConfirmed", confirmed);
        ReflectionTestUtils.setField(runner, "replaceDefault", replaceDefault);
        ReflectionTestUtils.setField(runner, "apply", apply);
        return runner;
    }
}