package com.radiotech.radiotech_backend.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.radiotech.radiotech_backend.model.Operator;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthQrLoginTest {
    @Test
    void firebaseSigningFailureDoesNotConsumeTheBadge() throws Exception {
        OperatorService operators = mock(OperatorService.class);
        FirebaseAuth firebase = mock(FirebaseAuth.class);
        Operator operator = operator();
        when(operators.validateQrToken("badge")).thenReturn(operator);
        UserRecord user = mock(UserRecord.class);
        when(user.getCustomClaims()).thenReturn(Map.of("tenantId", "tenant-a"));
        when(firebase.getUser("firebase-user")).thenReturn(user);
        when(firebase.createCustomToken(eq("firebase-user"), anyMap()))
                .thenThrow(new IllegalStateException("Signing unavailable"));
        try (var auth = mockStatic(FirebaseAuth.class)) {
            auth.when(FirebaseAuth::getInstance).thenReturn(firebase);
            assertThrows(IllegalStateException.class, () -> service(operators).loginWithQrToken("badge"));
            verify(operators, never()).consumeQrToken(anyString());
        }
    }

    @Test
    void successfulPreparationConsumesExactlyOnceBeforeReturningSession() throws Exception {
        OperatorService operators = mock(OperatorService.class);
        FirebaseAuth firebase = mock(FirebaseAuth.class);
        when(operators.validateQrToken("badge")).thenReturn(operator());
        when(operators.consumeQrToken("badge")).thenReturn(operator());
        UserRecord user = mock(UserRecord.class);
        when(user.getCustomClaims()).thenReturn(Map.of("tenantId", "tenant-a"));
        when(firebase.getUser("firebase-user")).thenReturn(user);
        when(firebase.createCustomToken(eq("firebase-user"), anyMap())).thenReturn("session-token");
        try (var auth = mockStatic(FirebaseAuth.class)) {
            auth.when(FirebaseAuth::getInstance).thenReturn(firebase);
            assertEquals("session-token", service(operators).loginWithQrToken("badge").get("customToken"));
            var order = inOrder(operators, firebase);
            order.verify(operators).validateQrToken("badge");
            order.verify(firebase).createCustomToken(eq("firebase-user"), anyMap());
            order.verify(operators).consumeQrToken("badge");
        }
    }

    @Test
    void concurrentRevocationPreventsPreparedTokenFromBeingDelivered() throws Exception {
        OperatorService operators = mock(OperatorService.class);
        FirebaseAuth firebase = mock(FirebaseAuth.class);
        when(operators.validateQrToken("badge")).thenReturn(operator());
        when(operators.consumeQrToken("badge")).thenThrow(new IllegalArgumentException("Revoked badge"));
        when(firebase.getUser("firebase-user")).thenReturn(mock(UserRecord.class));
        when(firebase.createCustomToken(eq("firebase-user"), anyMap())).thenReturn("session-token");
        try (var auth = mockStatic(FirebaseAuth.class)) {
            auth.when(FirebaseAuth::getInstance).thenReturn(firebase);
            assertThrows(IllegalArgumentException.class, () -> service(operators).loginWithQrToken("badge"));
        }
    }

    private Operator operator() {
        Operator operator = new Operator();
        operator.setId("operator-a");
        operator.setFirebaseUid("firebase-user");
        operator.setTenantId("tenant-a");
        return operator;
    }

    private AuthService service(OperatorService operators) {
        return new AuthService(operators, mock(CapoService.class), mock(RestClient.class));
    }
}
