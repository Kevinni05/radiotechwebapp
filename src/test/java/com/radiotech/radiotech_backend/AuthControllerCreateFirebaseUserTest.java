package com.radiotech.radiotech_backend;

import com.google.firebase.auth.UserRecord;
import com.radiotech.radiotech_backend.controller.AuthController;
import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationDetails;
import com.radiotech.radiotech_backend.service.AuthService;
import com.radiotech.radiotech_backend.service.OperatorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerCreateFirebaseUserTest {

        private final AuthService authService = mock(AuthService.class);
        private final OperatorService operatorService = mock(OperatorService.class);
        private final AuthController controller = new AuthController(authService, operatorService);

        @AfterEach
        void clearSecurityContext() {
                SecurityContextHolder.clearContext();
        }

        @Test
        void firebaseUserCreationUsesAuthenticatedTenantAndClaimAwareSync() throws Exception {
                authenticate("tenant-a");
                UserRecord user = mock(UserRecord.class);
                Operator operator = new Operator();
                operator.setId("operator-a");
                operator.setTenantId("tenant-a");
                when(authService.createFirebaseUser("operator@example.test", "SafePass!234", "Operator A"))
                                .thenReturn(user);
                when(user.getUid()).thenReturn("firebase-uid-a");
                when(user.getEmail()).thenReturn("operator@example.test");
                when(user.getDisplayName()).thenReturn("Operator A");
                when(operatorService.syncFirebaseUser("operator@example.test", "Operator A", "tenant-a"))
                                .thenReturn(operator);

                assertEquals(HttpStatus.CREATED, controller.createFirebaseUser(Map.of(
                                "email", "operator@example.test",
                                "password", "SafePass!234",
                                "displayName", "Operator A")).getStatusCode());
                verify(operatorService).syncFirebaseUser("operator@example.test", "Operator A", "tenant-a");
                verify(operatorService, never()).createOperator(org.mockito.ArgumentMatchers.any());
        }

        @Test
        void missingTenantDoesNotCreateFirebaseAccount() throws Exception {
                authenticate(null);

                assertEquals(HttpStatus.FORBIDDEN, controller.createFirebaseUser(Map.of(
                                "email", "operator@example.test",
                                "password", "SafePass!234")).getStatusCode());
                verify(authService, never()).createFirebaseUser(
                                eq("operator@example.test"), eq("SafePass!234"), eq(null));
        }

        @Test
        void failedTenantSyncRollsBackOperatorAndFirebaseAccount() throws Exception {
                authenticate("tenant-a");
                UserRecord user = mock(UserRecord.class);
                Operator partiallyCreated = new Operator();
                partiallyCreated.setId("operator-a");
                partiallyCreated.setTenantId("tenant-a");
                when(authService.createFirebaseUser("operator@example.test", "SafePass!234", "Operator A"))
                                .thenReturn(user);
                when(user.getUid()).thenReturn("firebase-uid-a");
                when(operatorService.syncFirebaseUser("operator@example.test", "Operator A", "tenant-a"))
                                .thenThrow(new IllegalStateException("claim update failed"));
                when(operatorService.getByFirebaseUid("firebase-uid-a")).thenReturn(partiallyCreated);

                assertEquals(HttpStatus.CONFLICT, controller.createFirebaseUser(Map.of(
                                "email", "operator@example.test",
                                "password", "SafePass!234",
                                "displayName", "Operator A")).getStatusCode());
                verify(operatorService).deleteOperator("operator-a");
                verify(authService).deleteFirebaseUser("firebase-uid-a");
        }

        private void authenticate(String tenantId) {
                var authentication = new UsernamePasswordAuthenticationToken(
                                "admin-a", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                authentication.setDetails(new FirebaseAuthenticationDetails(
                                "admin-a", "admin@example.test", "Admin", tenantId));
                SecurityContextHolder.getContext().setAuthentication(authentication);
        }
}