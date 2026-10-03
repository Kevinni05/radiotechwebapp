package com.radiotech.radiotech_backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.SecureRandom;
import java.util.Base64;

@Controller
public class DashboardPageController {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpServletResponse response) {
        byte[] nonceBytes = new byte[18];
        SECURE_RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        model.addAttribute("cspNonce", nonce);
        response.setHeader("Content-Security-Policy", "default-src 'self'; "
                + "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; "
                + "script-src 'self' 'nonce-" + nonce + "' https://unpkg.com; "
                + "style-src 'self' 'nonce-" + nonce + "' https://unpkg.com; "
                + "style-src-attr 'unsafe-inline'; "
                + "img-src 'self' data: blob: https://unpkg.com https://*.tile.openstreetmap.org; "
                + "font-src 'self' data: https://unpkg.com; connect-src 'self'");
        return "control-room.html";
    }
}
