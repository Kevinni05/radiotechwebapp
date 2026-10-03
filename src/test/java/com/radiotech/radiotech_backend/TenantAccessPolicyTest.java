package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.model.Task;
import com.radiotech.radiotech_backend.controller.TaskController;
import com.radiotech.radiotech_backend.dto.TaskDto;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.security.Permission;
import com.radiotech.radiotech_backend.security.Role;
import com.radiotech.radiotech_backend.security.SecurityContextAccessor;
import com.radiotech.radiotech_backend.security.TenantAccessPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.HttpStatus;
import com.radiotech.radiotech_backend.service.TaskService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TenantAccessPolicyTest {

    @Test
    void managerRoleHasRequiredPermission() {
        assertTrue(TenantAccessPolicy.canAccess(Role.SUPER_ADMIN, Permission.TASK_READ));
        assertTrue(TenantAccessPolicy.canAccess(Role.OPERATOR, Permission.TASK_READ));
        assertTrue(TenantAccessPolicy.canAccess(Role.VIEWER, Permission.REPORT_READ));
        assertFalse(TenantAccessPolicy.canAccess(Role.VIEWER, Permission.REPORT_APPROVE));
        assertFalse(TenantAccessPolicy.canAccess(Role.VIEWER, Permission.TASK_CREATE));
    }

    @Test
    void tenantIsolationPreventsCrossTenantAccess() {
        Task task = new Task();
        task.setTenantId("tenant-a");

        assertTrue(TenantAccessPolicy.canAccessTenant("tenant-a", "tenant-a"));
        assertFalse(TenantAccessPolicy.canAccessTenant("tenant-a", "tenant-b"));
        assertFalse(TenantAccessPolicy.canAccessTask(task, "tenant-b"));
        assertTrue(TenantAccessPolicy.canAccessTask(task, "tenant-a"));
    }

    @Test
    void authenticatedTenantComesFromVerifiedFirebaseDetails() {
        var authentication = new UsernamePasswordAuthenticationToken(
                "user-a",
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "user-a", "operator@example.test", "Operator A", "tenant-a"));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        try {
            assertEquals("tenant-a", SecurityContextAccessor.currentTenantId());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void tenantACannotSpoofTenantBInHeader() {
        TaskService taskService = mock(TaskService.class);
        TaskController controller = new TaskController(taskService);
        setAuthenticatedTenant("tenant-a");

        try {
            assertEquals(HttpStatus.FORBIDDEN, controller.getAllTasks("tenant-b").getStatusCode());
            verifyNoInteractions(taskService);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void tenantACannotSpoofTenantBInTaskBody() {
        TaskService taskService = mock(TaskService.class);
        TaskController controller = new TaskController(taskService);
        TaskDto request = new TaskDto();
        request.setTitle("Cross-tenant task");
        request.setTenantId("tenant-b");
        setAuthenticatedTenant("tenant-a");

        try {
            assertEquals(HttpStatus.FORBIDDEN, controller.createTask(request, null).getStatusCode());
            verifyNoInteractions(taskService);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void missingAuthenticatedTenantFailsClosed() {
        assertThrows(SecurityException.class, () -> TenantAccessPolicy.requireTenantAccess(null, "tenant-a"));
        assertThrows(SecurityException.class,
                () -> TenantAccessPolicy.requireTenantAccess("tenant-a", "tenant-b"));
    }

    private void setAuthenticatedTenant(String tenantId) {
        var authentication = new UsernamePasswordAuthenticationToken(
                "user-a",
                null,
                java.util.List.of(new SimpleGrantedAuthority("ROLE_OPERATOR")));
        authentication.setDetails(new FirebaseAuthenticationDetails(
                "user-a", "operator@example.test", "Operator A", tenantId));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
