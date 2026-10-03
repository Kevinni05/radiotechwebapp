package com.radiotech.radiotech_backend.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.ui.ExtendedModelMap;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardPageControllerTest {
    private final DashboardPageController controller = new DashboardPageController();

    @Test
    void dashboardGetsPerResponseNonceAndCompatibleCsp() {
        ExtendedModelMap firstModel = new ExtendedModelMap();
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        ExtendedModelMap secondModel = new ExtendedModelMap();
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();

        assertEquals("control-room.html", controller.dashboard(firstModel, firstResponse));
        assertEquals("control-room.html", controller.dashboard(secondModel, secondResponse));

        String firstNonce = (String) firstModel.get("cspNonce");
        assertTrue(firstNonce.matches("[A-Za-z0-9_-]{20,}"));
        assertTrue(firstResponse.getHeader("Content-Security-Policy").contains("'nonce-" + firstNonce + "'"));
        assertTrue(firstResponse.getHeader("Content-Security-Policy").contains("https://unpkg.com"));
        assertTrue(firstResponse.getHeader("Content-Security-Policy").contains("style-src-attr 'unsafe-inline'"));
        assertNotEquals(firstNonce, secondModel.get("cspNonce"));
    }

    @Test
    void controlRoomInlineBlocksConsumeTheModelNonce() throws Exception {
        String template = Files.readString(Path.of("src/main/resources/templates/control-room.html"));
        assertTrue(template.contains("<style th:attr=\"nonce=${cspNonce}\">"));
        assertTrue(template.contains("<script th:attr=\"nonce=${cspNonce}\">"));
    }
}
