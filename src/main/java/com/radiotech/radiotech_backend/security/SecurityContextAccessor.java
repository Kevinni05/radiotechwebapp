package com.radiotech.radiotech_backend.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityContextAccessor {

    private SecurityContextAccessor() {
    }

    public static Role currentRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Role.NONE;
        }

        String authority = authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .filter(authorityValue -> authorityValue != null && authorityValue.startsWith("ROLE_"))
                .findFirst()
                .orElse(null);

        if (authority == null) {
            return Role.NONE;
        }

        return Role.parse(authority.replace("ROLE_", ""));
    }

    public static String currentTenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getDetails() instanceof FirebaseAuthenticationDetails details)) {
            return null;
        }
        return details.getTenantId();
    }

    public static String currentUid() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null) {
            return null;
        }
        String uid = String.valueOf(authentication.getPrincipal()).trim();
        return uid.isBlank() || "anonymousUser".equals(uid) ? null : uid;
    }
}
