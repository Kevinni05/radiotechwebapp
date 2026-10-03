package com.radiotech.radiotech_backend.security;

import com.radiotech.radiotech_backend.model.Task;

import java.util.EnumSet;
import java.util.Set;

public final class TenantAccessPolicy {

    private TenantAccessPolicy() {
    }

    private static final Set<Permission> ADMIN_PERMISSIONS = EnumSet.allOf(Permission.class);
    private static final Set<Permission> MANAGER_PERMISSIONS = EnumSet.of(
            Permission.TASK_CREATE,
            Permission.TASK_READ,
            Permission.TASK_ASSIGN,
            Permission.TASK_START,
            Permission.TASK_COMPLETE,
            Permission.TASK_APPROVE,
            Permission.SITE_CREATE,
            Permission.SITE_READ,
            Permission.SITE_UPDATE,
            Permission.SITE_DELETE,
            Permission.ASSET_CREATE,
            Permission.ASSET_READ,
            Permission.ASSET_UPDATE,
            Permission.ASSET_DELETE,
            Permission.REPORT_CREATE,
            Permission.REPORT_READ,
            Permission.REPORT_APPROVE,
            Permission.ALERT_CREATE,
            Permission.ALERT_READ,
            Permission.ALERT_ACK,
            Permission.ALERT_EVALUATE,
            Permission.INCIDENT_READ,
            Permission.INCIDENT_CREATE,
            Permission.INCIDENT_UPDATE,
            Permission.INVENTORY_READ,
            Permission.INVENTORY_WRITE,
            Permission.USER_READ,
            Permission.USER_CREATE,
            Permission.USER_UPDATE,
            Permission.USER_DELETE,
            Permission.AUDIT_READ,
            Permission.ANALYTICS_READ);
    private static final Set<Permission> OPERATOR_PERMISSIONS = EnumSet.of(
            Permission.TASK_READ,
            Permission.TASK_START,
            Permission.TASK_COMPLETE,
            Permission.SITE_READ,
            Permission.ASSET_READ,
            Permission.REPORT_CREATE,
            Permission.ALERT_READ,
            Permission.ALERT_ACK,
            Permission.INCIDENT_READ,
            Permission.INVENTORY_READ,
            Permission.INVENTORY_WRITE,
            Permission.USER_READ,
            Permission.ANALYTICS_READ);
    private static final Set<Permission> VIEWER_PERMISSIONS = EnumSet.of(
            Permission.TASK_READ,
            Permission.SITE_READ,
            Permission.ASSET_READ,
            Permission.USER_READ,
            Permission.REPORT_READ,
            Permission.ALERT_READ,
            Permission.AUDIT_READ,
            Permission.ANALYTICS_READ);

    public static boolean canAccess(Role role, Permission permission) {
        if (role == null || permission == null) {
            return false;
        }

        return switch (role) {
            case SUPER_ADMIN -> ADMIN_PERMISSIONS.contains(permission);
            case ADMIN, CHIEF_EXECUTIVE, NETWORK_MANAGER, ENGINEER -> MANAGER_PERMISSIONS.contains(permission);
            case OPERATOR -> OPERATOR_PERMISSIONS.contains(permission);
            case VIEWER -> VIEWER_PERMISSIONS.contains(permission);
            case NONE -> false;
        };
    }

    public static boolean canAccessTenant(String tenantId, String requestedTenantId) {
        if (tenantId == null || tenantId.isBlank()
                || requestedTenantId == null || requestedTenantId.isBlank()) {
            return false;
        }
        return tenantId.equals(requestedTenantId);
    }

    public static String requireTenantAccess(String authenticatedTenantId, String requestedTenantId) {
        if (authenticatedTenantId == null || authenticatedTenantId.isBlank()) {
            throw new SecurityException("Tenant non assegnato all'identita' autenticata.");
        }
        if (requestedTenantId != null && !requestedTenantId.isBlank()
                && !authenticatedTenantId.equals(requestedTenantId.trim())) {
            throw new SecurityException("Tenant non autorizzato.");
        }
        return authenticatedTenantId;
    }

    public static boolean canAccessTask(Task task, String tenantId) {
        if (task == null) {
            return false;
        }
        String taskTenantId = task.getTenantId();
        return canAccessTenant(taskTenantId, tenantId);
    }
}
