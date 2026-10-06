package com.radiotech.radiotech_backend.service;
import com.radiotech.radiotech_backend.dto.AiChatRequest;
import com.radiotech.radiotech_backend.security.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AiConversationServiceTest {
    @BeforeEach void identity() { var auth = new UsernamePasswordAuthenticationToken("test-operator", null, List.of(new SimpleGrantedAuthority("ROLE_OPERATOR"))); auth.setDetails(new FirebaseAuthenticationDetails("test-operator", "test@example.test", "Test", "tenant-test")); SecurityContextHolder.getContext().setAuthentication(auth); }
    @AfterEach void clean() { SecurityContextHolder.clearContext(); }
    @Test void disabledProviderAndMissingIdentityFailClosed() {
        var service = new AiConversationService(false, "http://127.0.0.1:11434", "qwen3:4b", mock(OperationalInsightsService.class));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.chat(new AiChatRequest("ciao", List.of(), false))).getStatusCode().value());
        SecurityContextHolder.clearContext(); assertThrows(SecurityException.class, service::status);
    }
    @Test void onlyAuthorizedAggregatesAreSentAndPromptHistoryIsBoundedByContract() throws Exception {
        var payload = new AtomicReference<String>(); var server = server(200, "{\"message\":{\"content\":\"Risposta reale del provider\"}}", payload);
        try {
            var insights = mock(OperationalInsightsService.class);
            when(insights.insights()).thenReturn(Map.of("scope", "PERSONAL", "activeTasks", 2, "overdueTasks", 1, "highRiskAssets", 0, "partial", false, "method", "EXPLAINABLE_RULES_V1", "sensitive", "private-token"));
            var service = new AiConversationService(true, origin(server), "test-model", insights);
            var result = service.chat(new AiChatRequest("Spiega gli incarichi", List.of(), true));
            assertEquals("Risposta reale del provider", result.get("answer")); assertTrue(payload.get().contains("PERSONAL")); assertFalse(payload.get().contains("private-token")); assertFalse(payload.get().contains("test@example.test"));
            for (int i = 1; i < 12; i++) service.chat(new AiChatRequest("ciao", List.of(), false));
            assertEquals(429, assertThrows(ResponseStatusException.class, () -> service.chat(new AiChatRequest("ciao", List.of(), false))).getStatusCode().value());
        } finally { server.stop(0); }
    }
    @Test void redirectsAndOversizedProviderResponsesAreRejected() throws Exception {
        for (int status : List.of(302, 200)) {
            var server = server(status, status == 200 ? "x".repeat(100_001) : "{}", new AtomicReference<>());
            try { var service = new AiConversationService(true, origin(server), "test", mock(OperationalInsightsService.class)); assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.chat(new AiChatRequest("ciao", List.of(), false))).getStatusCode().value()); }
            finally { server.stop(0); }
        }
    }
    @Test @EnabledIfEnvironmentVariable(named = "RADIOTECH_AI_SMOKE_TEST", matches = "true")
    void realLocalModelProducesAResponse() throws Exception {
        var service = new AiConversationService(true, "http://127.0.0.1:11434", "qwen3:4b", mock(OperationalInsightsService.class));
        var result = service.chat(new AiChatRequest("Rispondi in italiano con una frase di benvenuto di massimo dieci parole.", List.of(), false));
        assertFalse(result.get("answer").toString().isBlank()); assertEquals("qwen3:4b", result.get("model"));
    }
    private HttpServer server(int status, String body, AtomicReference<String> payload) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/chat", exchange -> { payload.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)); byte[] bytes = body.getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(status, bytes.length); exchange.getResponseBody().write(bytes); exchange.close(); }); server.start(); return server;
    }
    private String origin(HttpServer server) { return "http://127.0.0.1:" + server.getAddress().getPort(); }
}
