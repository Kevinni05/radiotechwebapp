package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.model.Operator;
import com.radiotech.radiotech_backend.service.*;
import com.radiotech.radiotech_backend.security.TenantInvitationService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class OperatorDeviceTokenControllerTest {
    @Test void removesOnlyCurrentOperatorsDeviceAndIsReplaySafe() throws Exception {
        var operators = mock(OperatorService.class);
        var operator = new Operator(); operator.setId("operator-a");
        when(operators.getByFirebaseUid("user-a")).thenReturn(operator);
        var controller = new OperatorMobileController(mock(TaskService.class), mock(MaintenanceReportService.class),
                operators, mock(TenantInvitationService.class));
        for (int replay = 0; replay < 2; replay++) {
            assertEquals(HttpStatus.OK, controller.removeMyFcmToken("user-a", Map.of("fcmToken", "device-a")).getStatusCode());
        }
        verify(operators, times(2)).removeFcmToken("operator-a", "device-a");
        verify(operators, times(2)).getByFirebaseUid("user-a");
        assertEquals(HttpStatus.BAD_REQUEST, controller.removeMyFcmToken("user-a", null).getStatusCode());
        assertEquals(HttpStatus.NOT_FOUND, controller.removeMyFcmToken("other-user", Map.of("fcmToken", "device-a")).getStatusCode());
        verify(operators).getByFirebaseUid("other-user");
        verifyNoMoreInteractions(operators);
    }
}
