package com.radiotech.radiotech_backend.controller;

import com.radiotech.radiotech_backend.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

import java.net.http.HttpTimeoutException;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(OutputCaptureExtension.class)
class WeatherFailureTest {
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer provider = MockRestServiceServer.bindTo(builder).build();
    private final WeatherController controller = new WeatherController(builder.build());

    private MockMvc web() {
        ReflectionTestUtils.setField(controller, "endpoint", "https://weather.example.test/forecast");
        ReflectionTestUtils.setField(controller, "geocodingEndpoint", "https://weather.example.test/search");
        ReflectionTestUtils.setField(controller, "apiKey", "fixture-private-key");
        return MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"401, rifiutato", "403, rifiutato", "429, Limite", "500, HTTP 500", "503, HTTP 503"})
    void providerFailuresAreActionableAndDoNotExposeCredentials(int status, String message, CapturedOutput logs) throws Exception {
        provider.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status))
                .body("upstream-private-body fixture-private-key").contentType(MediaType.TEXT_PLAIN));
        web().perform(get("/api/v1/weather").param("latitude", "40.35481").param("longitude", "18.17244"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message", containsString(message)))
                .andExpect(jsonPath("$.success").value(false));
        assertTrue(logs.getOut().contains("upstreamStatus=" + status));
        assertFalse(logs.getAll().contains("fixture-private-key"));
        assertFalse(logs.getAll().contains("upstream-private-body"));
        provider.verify();
    }

    @Test void timeoutIsIdentifiedInServerLogsAndResponse(CapturedOutput logs) throws Exception {
        provider.expect(anything()).andRespond(request -> { throw new HttpTimeoutException("private transport detail"); });
        web().perform(get("/api/v1/weather").param("latitude", "40").param("longitude", "18"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message", containsString("in tempo")));
        assertTrue(logs.getOut().contains("category=TIMEOUT"));
        assertFalse(logs.getAll().contains("private transport detail"));
        provider.verify();
    }

    @Test void invalidForecastIsRejectedInsteadOfCachedAsSuccess() throws Exception {
        provider.expect(anything()).andRespond(withSuccess("{\"current\":{}}", MediaType.APPLICATION_JSON));
        provider.expect(anything()).andRespond(withSuccess("{\"current\":{\"temperature_2m\":24}}", MediaType.APPLICATION_JSON));
        var mvc = web();
        mvc.perform(get("/api/v1/weather").param("latitude", "40").param("longitude", "18"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message", containsString("dati non validi")));
        mvc.perform(get("/api/v1/weather").param("latitude", "40").param("longitude", "18"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.temperature_2m").value(24));
        assertEquals(24, controller.current(40, 18).get("temperature_2m"));
        provider.verify();
    }

    @Test void locationSearchAlsoReturnsProviderFailureInsteadOfGeneric500() throws Exception {
        provider.expect(anything()).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        web().perform(get("/api/v1/weather/locations").param("query", "Lecce"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message", containsString("Limite")));
        provider.verify();
    }
}
