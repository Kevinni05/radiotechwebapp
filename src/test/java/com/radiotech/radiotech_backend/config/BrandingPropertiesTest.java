package com.radiotech.radiotech_backend.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrandingPropertiesTest {

    @Test
    void acceptsCustomerBrandingAndLocalLogoPath() {
        BrandingProperties branding = new BrandingProperties(
                "Acme Field Ops",
                "Network Operations",
                "Acme S.p.A.",
                "support@acme.example",
                "/assets/brand/acme.png",
                "© Acme");

        assertEquals("Acme Field Ops", branding.productName());
        assertEquals("Network Operations", branding.productSubtitle());
        assertEquals("Acme S.p.A.", branding.companyName());
        assertEquals("/assets/brand/acme.png", branding.logoPath());
    }

    @Test
    void rejectsRemoteOrTraversalLogoPaths() {
        assertThrows(IllegalArgumentException.class, () -> new BrandingProperties(
                "Acme", "Ops", "Acme", "", "https://cdn.example/logo.png", ""));
        assertThrows(IllegalArgumentException.class, () -> new BrandingProperties(
                "Acme", "Ops", "Acme", "", "/assets/../secret.png", ""));
    }
}
