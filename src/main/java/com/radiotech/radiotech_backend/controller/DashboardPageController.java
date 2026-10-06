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
    @GetMapping("/portal")
    public String portal(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; style-src 'self'; style-src-attr 'unsafe-inline'; script-src 'self'; connect-src 'self' https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://*.firebaseapp.com; frame-src https://*.firebaseapp.com");
        return "customer-portal.html";
    }

    @GetMapping({"/", "/login", "/dashboard"})
    public String dashboard(Model model, HttpServletResponse response) {
        byte[] nonceBytes = new byte[18];
        SECURE_RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        model.addAttribute("cspNonce", nonce);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        response.setHeader("Content-Security-Policy", "default-src 'self'; "
                + "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; "
                + "script-src 'self' 'nonce-" + nonce + "'; "
                + "style-src 'self' 'nonce-" + nonce + "'; "
                + "style-src-attr 'unsafe-inline'; "
                + "img-src 'self' data: blob: https://*.tile.openstreetmap.org; "
                + "font-src 'self' data:; connect-src 'self' https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://*.firebaseapp.com; frame-src https://*.firebaseapp.com");
        return "control-room.html";
    }
}
