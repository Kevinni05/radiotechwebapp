package com.radiotech.radiotech_backend.service;

import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class NotificationErrorCodeTest {
    private String code(FirebaseMessagingException error) throws Exception {
        var response=mock(SendResponse.class);
        when(response.getException()).thenReturn(error);
        var method=NotificationService.class.getDeclaredMethod("messagingCode", SendResponse.class);
        method.setAccessible(true);
        return (String)method.invoke(null,response);
    }
    @Test void permissionFailureRemainsActionableWithoutLoggingItsMessageOrToken() throws Exception {
        var error=mock(FirebaseMessagingException.class);
        when(error.getErrorCode()).thenReturn(ErrorCode.PERMISSION_DENIED);
        assertEquals("PERMISSION_DENIED",code(error));
    }
    @Test void tokenExpiryKeepsItsSpecificMessagingCode() throws Exception {
        var error=mock(FirebaseMessagingException.class);
        when(error.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(error.getErrorCode()).thenReturn(ErrorCode.NOT_FOUND);
        assertEquals("UNREGISTERED",code(error));
    }
}
