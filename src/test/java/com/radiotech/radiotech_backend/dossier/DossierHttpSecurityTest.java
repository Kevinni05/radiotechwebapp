package com.radiotech.radiotech_backend.dossier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Map;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "app.firebase.enabled=false")
class DossierHttpSecurityTest {
  @Autowired WebApplicationContext context;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  FilterChainProxy filters;

  @MockitoBean DossierService dossier;
  MockMvc mvc;

  @BeforeEach
  void setup() {
    mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(filters).build();
  }

  @Test
  void bearerlessShareReadUsesOnlyTheDedicatedPublicRoute() throws Exception {
    when(dossier.publicRead(any()))
        .thenReturn(
            Map.of(
                "snapshot",
                Map.of("name", "Impianto condiviso", "identity", Map.of("model", "Radio X")),
                "expiresAt",
                "2027-01-01T00:00:00Z"));
    mvc.perform(
            post("/api/v1/dossier-shares/read")
                .contentType("application/json")
                .content("{\"token\":\"" + "a".repeat(43) + "\"}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(header().string("Referrer-Policy", "no-referrer"))
        .andExpect(jsonPath("$.snapshot.name").value("Impianto condiviso"))
        .andExpect(jsonPath("$.tenantId").doesNotExist());
  }

  @Test
  void privateDossierAndSessionRoutesRemainAuthenticated() throws Exception {
    mvc.perform(get("/api/v1/antennas/asset/dossier")).andExpect(status().isUnauthorized());
    mvc.perform(get("/api/v1/radio-pair-sessions/session")).andExpect(status().isUnauthorized());
    verifyNoInteractions(dossier);
  }

  @Test
  void invalidOrRevokedPublicTokenIsDenied() throws Exception {
    when(dossier.publicRead(any()))
        .thenThrow(new SecurityException("Link non disponibile o scaduto."));
    mvc.perform(
            post("/api/v1/dossier-shares/read")
                .contentType("application/json")
                .content("{\"token\":\"invalid\"}"))
        .andExpect(status().isForbidden());
  }
}
