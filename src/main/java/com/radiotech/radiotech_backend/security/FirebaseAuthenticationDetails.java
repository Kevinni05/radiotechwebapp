package com.radiotech.radiotech_backend.security;

public class FirebaseAuthenticationDetails {

    private final String uid;
    private final String email;
    private final String displayName;
    private final String tenantId;

    public FirebaseAuthenticationDetails(
            String uid,
            String email,
            String displayName,
            String tenantId) {

        this.uid = uid;
        this.email = email;
        this.displayName = displayName;
        this.tenantId = tenantId;
    }

    public String getUid() {
        return uid;
    }

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