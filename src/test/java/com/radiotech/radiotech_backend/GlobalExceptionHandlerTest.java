package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticationErrorsReturn401() {
        var response = handler.handleAuthentication(new AuthenticationException("not authenticated") {
        });

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, ((Map<?, ?>) response.getBody()).get("status"));
        assertEquals("Unauthorized", ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void forbiddenErrorsReturn403() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user-1", null, java.util.List.of()));
        var response = handler.handleForbidden(new AccessDeniedException("forbidden"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, ((Map<?, ?>) response.getBody()).get("status"));
        assertEquals("Forbidden", ((Map<?, ?>) response.getBody()).get("error"));
    }

    @Test
    void unauthenticatedSecurityErrorsReturn401() {
        var response = handler.handleSecurity(new SecurityException("authentication required"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, ((Map<?, ?>) response.getBody()).get("status"));
    }

    @Test
    void authenticatedSecurityErrorsRemain403() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("user-1", null, java.util.List.of()));

        var response = handler.handleSecurity(new SecurityException("tenant boundary"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(403, ((Map<?, ?>) response.getBody()).get("status"));
    }
}
