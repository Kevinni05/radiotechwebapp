package com.radiotech.radiotech_backend.controller;

import com.google.firebase.auth.FirebaseToken;
import com.radiotech.radiotech_backend.service.AuthService;
import com.radiotech.radiotech_backend.service.OperatorService;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthChangePasswordControllerTest {
    @Test
    void ignoresClientSuppliedUidAndEmail() {
        var service = mock(AuthService.class);
        var token = mock(FirebaseToken.class);
        when(token.getEmail()).thenReturn("capo@example.test");
        var controller = new AuthController(service, mock(OperatorService.class));
        var response = controller.changePassword("chief-uid", token, Map.of(
            "uid", "other-uid", "email", "other@example.test", "currentPassword", "OldPass1!", "newPassword", "NewPass2!"));
        assertEquals(200, response.getStatusCode().value());
        verify(service).changePassword("chief-uid", "capo@example.test", "OldPass1!", "NewPass2!");
    }

    @Test
    void incorrectCurrentPasswordDoesNotExpireExistingSession() {
        var service = mock(AuthService.class);
        var token = mock(FirebaseToken.class);
        when(token.getEmail()).thenReturn("capo@example.test");
        doThrow(new IllegalArgumentException("Password attuale non corretta."))
            .when(service).changePassword(anyString(), anyString(), anyString(), anyString());
        var controller = new AuthController(service, mock(OperatorService.class));
        assertEquals(400, controller.changePassword("chief-uid", token,
            Map.of("currentPassword", "wrong", "newPassword", "NewPass2!")).getStatusCode().value());
    }
}
