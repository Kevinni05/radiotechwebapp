package com.radiotech.radiotech_backend.exception;

import com.radiotech.radiotech_backend.service.MaintenanceReportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handlesEntityNotFoundException() {
        ResponseEntity<Map<String, Object>> response = handler.handleEntityNotFound(
                new EntityNotFoundException("record missing"));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(404, response.getBody().get("status"));
        assertEquals("record missing", response.getBody().get("message"));
    }

    @Test
    void handlesHttpMessageNotReadableException() {
        ResponseEntity<Map<String, Object>> response = handler.handleHttpMessageNotReadable(
                new HttpMessageNotReadableException("invalid JSON payload", new RuntimeException("bad json"), null));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(400, response.getBody().get("status"));
        assertEquals("Payload non valido o corrotto.", response.getBody().get("message"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().toString().contains("bad json"));
    }

    @Test
    void hidesAuthenticationProviderDetails() {
        ResponseEntity<Map<String, Object>> response = handler.handleAuthentication(
                new org.springframework.security.authentication.BadCredentialsException("private provider detail"));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Autenticazione non valida o scaduta.", response.getBody().get("message"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().toString().contains("private provider detail"));
    }

    @Test
    void hidesAuthorizationExceptionDetails() {
        ResponseEntity<Map<String, Object>> response = handler.handleForbidden(
                new org.springframework.security.access.AccessDeniedException("private policy detail"));

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals("Permesso insufficiente.", response.getBody().get("message"));
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().toString().contains("private policy detail"));
    }

    @Test
    void handlesIdempotencyConflictException() {
        ResponseEntity<Map<String, Object>> response = handler.handleIdempotencyConflict(
                new MaintenanceReportService.IdempotencyConflictException());

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals(409, response.getBody().get("status"));
        assertNotNull(response.getBody().get("message"));
    }
}
