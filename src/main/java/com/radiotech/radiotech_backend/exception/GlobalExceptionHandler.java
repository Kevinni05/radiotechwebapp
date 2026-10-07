package com.radiotech.radiotech_backend.exception;

import com.radiotech.radiotech_backend.service.MaintenanceReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
        @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
        public ResponseEntity<Map<String,Object>> missingResource(org.springframework.web.servlet.resource.NoResourceFoundException exception) {
                return buildResponse(HttpStatus.NOT_FOUND, "Risorsa non disponibile.");
        }

        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<Map<String, Object>> handleIllegalArgument(
                        IllegalArgumentException exception) {

                return buildResponse(
                                HttpStatus.BAD_REQUEST,
                                exception.getMessage());
        }

        @ExceptionHandler(EntityNotFoundException.class)
        public ResponseEntity<Map<String, Object>> handleEntityNotFound(
                        EntityNotFoundException exception) {

                return buildResponse(
                                HttpStatus.NOT_FOUND,
                                exception.getMessage());
        }

        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
                        HttpMessageNotReadableException exception) {

                return buildResponse(
                                HttpStatus.BAD_REQUEST,
                                "Payload non valido o corrotto.");
        }

        @ExceptionHandler(AuthenticationException.class)
        public ResponseEntity<Map<String, Object>> handleAuthentication(
                        AuthenticationException exception) {

                return buildResponse(
                                HttpStatus.UNAUTHORIZED,
                        "Autenticazione non valida o scaduta.");
        }

        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Map<String, Object>> handleForbidden(
                        AccessDeniedException exception) {

                return buildResponse(
                                HttpStatus.FORBIDDEN,
                        "Permesso insufficiente.");
        }

        @ExceptionHandler(SecurityException.class)
        public ResponseEntity<Map<String, Object>> handleSecurity(
                        SecurityException exception) {

                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !authentication.isAuthenticated()
                                || authentication.getPrincipal() == null
                                || "anonymousUser".equals(String.valueOf(authentication.getPrincipal()))) {
                        return buildResponse(HttpStatus.UNAUTHORIZED, "Autenticazione richiesta.");
                }

                return buildResponse(
                                HttpStatus.FORBIDDEN,
                        "Permesso insufficiente.");
        }

        @ExceptionHandler({MaintenanceReportService.IdempotencyConflictException.class, com.radiotech.radiotech_backend.service.TaskService.IdempotencyConflictException.class})
        public ResponseEntity<Map<String, Object>> handleIdempotencyConflict(
                        RuntimeException exception) {

                return buildResponse(
                                HttpStatus.CONFLICT,
                                "Conflitto di idempotenza: la stessa richiesta è già stata processata con un payload diverso.");
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
                String message = exception.getBindingResult().getFieldErrors().stream()
                                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                                .findFirst()
                                .orElse("Dati della richiesta non validi.");
                return buildResponse(HttpStatus.BAD_REQUEST, message);
        }

        @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
        public ResponseEntity<Map<String, Object>> handleResponseStatus(org.springframework.web.server.ResponseStatusException exception) {
                HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
                return buildResponse(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, exception.getReason());
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<Map<String, Object>> handleException(
                        Exception exception) {

                if (CloudQuota.exhausted(exception)) return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, CloudQuota.MESSAGE);
                return buildResponse(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Si è verificato un errore interno al server.");
        }

        private ResponseEntity<Map<String, Object>> buildResponse(
                        HttpStatus status,
                        String message) {

                Map<String, Object> response = new LinkedHashMap<>();

                response.put("success", false);
                response.put("status", status.value());
                response.put("error", status.getReasonPhrase());
                response.put(
                                "message",
                                message == null || message.isBlank()
                                                ? status.getReasonPhrase()
                                                : message);
                response.put(
                                "timestamp",
                                Instant.now().toString());

                return ResponseEntity
                                .status(status)
                                .body(response);
        }
}
