package com.radiotech.radiotech_backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runtime branding for buyer deployments.
 *
 * Keeps the product white-labelable without recompiling the backend. Values are
 * intentionally presentation-only and never used as security identifiers.
 */
@Component
public class BrandingProperties {
    private final String productName;
    private final String productSubtitle;
    private final String companyName;
    private final String supportEmail;
    private final String logoPath;
    private final String legalText;

    public BrandingProperties(
            @Value("${radiotech.branding.product-name:RadioTech}") String productName,
            @Value("${radiotech.branding.product-subtitle:Gestione operativa}") String productSubtitle,
            @Value("${radiotech.branding.company-name:RadioTech}") String companyName,
            @Value("${radiotech.branding.support-email:}") String supportEmail,
            @Value("${radiotech.branding.logo-path:/assets/brand/radiotech-symbol-v1.png}") String logoPath,
            @Value("${radiotech.branding.legal-text:}") String legalText) {
        this.productName = productName.trim();
        this.productSubtitle = productSubtitle.trim();
        this.companyName = companyName.trim();
        this.supportEmail = supportEmail.trim();
        this.logoPath = normalizeLogoPath(logoPath);
        this.legalText = legalText.trim();
    }

    public String productName() {
        return productName;
    }

    public String productSubtitle() {
        return productSubtitle;
    }

    public String companyName() {
        return companyName;
    }

    public String supportEmail() {
        return supportEmail;
    }

    public String logoPath() {
        return logoPath;
    }

    public String legalText() {
        return legalText;
    }

    private static String normalizeLogoPath(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return "/assets/brand/radiotech-symbol-v1.png";
        }
        if (!normalized.startsWith("/") || normalized.contains("://") || normalized.contains("..")) {
            throw new IllegalArgumentException("Brand logo path must be an application-local absolute path");
        }
        return normalized;
    }
}
