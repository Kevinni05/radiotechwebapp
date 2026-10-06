package com.radiotech.radiotech_backend.security;

public class FirebaseAuthenticationDetails {

    private final String uid;
    private final String email;
    private final String displayName;
    private final String tenantId;
    private final String customerId;
    private final java.util.List<String> proPermissions;

    public FirebaseAuthenticationDetails(
            String uid,
            String email,
            String displayName,
            String tenantId) {
        this(uid,email,displayName,tenantId,null);
    }
    public FirebaseAuthenticationDetails(String uid, String email, String displayName, String tenantId, String customerId) {
        this(uid,email,displayName,tenantId,customerId,null);
    }
    public FirebaseAuthenticationDetails(String uid, String email, String displayName, String tenantId, String customerId, java.util.List<String> proPermissions) {
        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.proPermissions = proPermissions == null ? null : java.util.List.copyOf(proPermissions);
    }

    public String getUid() {
        return uid;
    }
    public String getCustomerId() { return customerId; }
    public java.util.List<String> getProPermissions() { return proPermissions; }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTenantId() {
        return tenantId;
    }
}
