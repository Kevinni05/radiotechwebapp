package com.radiotech.radiotech_backend;

import com.radiotech.radiotech_backend.controller.OperatorNotificationController;
import com.radiotech.radiotech_backend.security.FirebaseAuthenticationFilter;
import com.radiotech.radiotech_backend.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OperatorNotificationControllerTest {
    @Test void routeUsesUidResolvedFromTheSession() throws Exception {
        NotificationService notifications = mock(NotificationService.class);
        when(notifications.getOperatorHistory("verified-uid")).thenReturn(List.of(Map.of("title", "Message")));
        var mvc = MockMvcBuilders.standaloneSetup(new OperatorNotificationController(notifications)).build();
        mvc.perform(get("/api/v1/operator/me/notifications").requestAttr("firebaseUid", "verified-uid")
                .param("operatorId", "someone-else"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].title").value("Message"));
        verify(notifications).getOperatorHistory("verified-uid");
    }
    @Test void unauthenticatedRequestsCannotReadAnyHistory() throws Exception {
        NotificationService notifications = mock(NotificationService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new OperatorNotificationController(notifications))
                .addFilters(new FirebaseAuthenticationFilter()).build();
        mvc.perform(get("/api/v1/operator/me/notifications")).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }
}
