package com.radiotech.radiotech_backend.controller;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;

class PublicAuthConfigControllerTest {
    @Test void managedPasswordAuthDoesNotRequireEnablingFederatedProviders() {
        var controller = new PublicAuthConfigController();
        ReflectionTestUtils.setField(controller, "key", "public-browser-api-key");
        ReflectionTestUtils.setField(controller, "project", "demo-radiotech");
        ReflectionTestUtils.setField(controller, "enabled", false);
        assertEquals(true, controller.config().get("enabled"));
        assertEquals(false, controller.config().get("federatedLoginEnabled"));
        ReflectionTestUtils.setField(controller, "enabled", true);
        assertEquals(true, controller.config().get("federatedLoginEnabled"));
        ReflectionTestUtils.setField(controller, "key", "");
        assertEquals(false, controller.config().get("enabled"));
        assertEquals(false, controller.config().get("federatedLoginEnabled"));
    }
}
