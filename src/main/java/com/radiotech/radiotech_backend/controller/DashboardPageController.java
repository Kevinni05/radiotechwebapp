package com.radiotech.radiotech_backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.SecureRandom;
import java.util.Base64;

@Controller
public class DashboardPageController {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${radiotech.brand.name:RadioTech}")
    private String brandName = "RadioTech";

    @Value("${radiotech.brand.subtitle:Gestione operativa}")
    private String brandSubtitle = "Gestione operativa";

    @Value("${radiotech.brand.logo-path:/assets/brand/radiotech-symbol-v1.png}")
    private String brandLogoPath = "/assets/brand/radiotech-symbol-v1.png";

    @Value("${radiotech.brand.copyright:© 2026 RadioTech. Tutti i diritti riservati.}")
    private String brandCopyright = "© 2026 RadioTech. Tutti i diritti riservati.";

    @Value("${radiotech.brand.support-email:}")
    private String brandSupportEmail = "";

    private void addBrand(Model model) {
        model.addAttribute("brandName", brandName);
        model.addAttribute("brandSubtitle", brandSubtitle);
        model.addAttribute("brandLogoPath", safeLogoPath(brandLogoPath));
        model.addAttribute("brandCopyright", brandCopyright);
        model.addAttribute("brandSupportEmail", brandSupportEmail);
    }

    private String safeLogoPath(String value) {
        if (value != null && value.startsWith("/assets/brand/") && !value.contains("..")) {
            return value;
        }
        return "/assets/brand/radiotech-symbol-v1.png";
    }
    @GetMapping("/portal")
    public String portal(Model model, HttpServletResponse response) {
        addBrand(model);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Content-Security-Policy", "default-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'; style-src 'self'; style-src-attr 'unsafe-inline'; script-src 'self'; connect-src 'self' https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://*.firebaseapp.com; frame-src https://*.firebaseapp.com");
        return "customer-portal.html";
    }

    @GetMapping({"/", "/login", "/dashboard"})
    public String dashboard(Model model, HttpServletResponse response) {
        addBrand(model);
        byte[] nonceBytes = new byte[18];
        SECURE_RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        model.addAttribute("cspNonce", nonce);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(self)");
        response.setHeader("Content-Security-Policy", "default-src 'self'; "
                + "base-uri 'self'; object-src 'none'; frame-ancestors 'none'; form-action 'self'; "
                + "script-src 'self' 'nonce-" + nonce + "'; "
                + "style-src 'self' 'nonce-" + nonce + "'; "
                + "style-src-attr 'unsafe-inline'; "
                + "img-src 'self' data: blob: https://*.tile.openstreetmap.org; "
                + "font-src 'self' data:; connect-src 'self' https://identitytoolkit.googleapis.com https://securetoken.googleapis.com https://*.firebaseapp.com https://firebasestorage.googleapis.com https://firestore.googleapis.com; worker-src 'self'; frame-src https://*.firebaseapp.com");
        return "control-room.html";
    }
}
