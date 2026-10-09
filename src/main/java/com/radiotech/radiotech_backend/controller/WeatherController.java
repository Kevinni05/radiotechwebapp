package com.radiotech.radiotech_backend.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(WeatherController.class);
    private record Cached(Instant at, Map<String,Object> data) {}
    private final Map<String,Cached> cache = new ConcurrentHashMap<>();
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.endpoint:https://api.open-meteo.com/v1/forecast}") private String endpoint;
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.api-key:}") private String apiKey;
    public WeatherController() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(8)); client = RestClient.builder().requestFactory(factory).build();
    }
    WeatherController(RestClient client) { this.client = client; }
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.geocoding-endpoint:https://geocoding-api.open-meteo.com/v1/search}") private String geocodingEndpoint;
    @GetMapping("/locations") public Map<String,Object> locations(@RequestParam String query) {
        String name=query.strip();
        if(name.length()<2||name.length()>100)throw new IllegalArgumentException("Inserisci una località da 2 a 100 caratteri.");
        String key="location:"+name.toLowerCase(Locale.ROOT);
        var entry=cache.get(key); if(entry!=null&&entry.at().plusSeconds(3600).isAfter(Instant.now()))return entry.data();
        var uri=org.springframework.web.util.UriComponentsBuilder.fromUriString(geocodingEndpoint).queryParam("name",name).queryParam("count",8).queryParam("language","it").queryParam("format","json");
        Map<?,?> response=providerData(uri.build().encode().toUri(), "locations");
        Map<String,Object> data=new LinkedHashMap<>(); data.put("results",response.get("results") instanceof List<?> results?results:List.of());
        if(cache.size()>500)cache.clear(); cache.put(key,new Cached(Instant.now(),data)); return data;
    }
    @GetMapping public Map<String,Object> current(@RequestParam double latitude, @RequestParam double longitude) {
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||latitude < -90||latitude>90||longitude < -180||longitude>180) throw new IllegalArgumentException("Coordinate meteo non valide.");
        String key = String.format(Locale.ROOT,"%.2f,%.2f",latitude,longitude);
        var entry = cache.get(key); if(entry!=null && entry.at().plusSeconds(600).isAfter(Instant.now()))return entry.data();
        var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(endpoint).queryParam("latitude",latitude).queryParam("longitude",longitude).queryParam("current","temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m").queryParam("daily","weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max").queryParam("forecast_days",6).queryParam("timezone","auto");
        if(apiKey!=null&&!apiKey.isBlank())uri.queryParam("apikey",apiKey);
        Map<?,?> response = providerData(uri.build().encode().toUri(), "forecast");
        if(!(response.get("current") instanceof Map<?,?> current) || !(current.get("temperature_2m") instanceof Number)) {
            log.warn("Weather provider failure: operation=forecast category=INVALID_RESPONSE");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Il servizio meteo ha restituito dati non validi. Riprova più tardi.");
        }
        Map<String,Object> data=new LinkedHashMap<>(); current.forEach((k,v)->data.put(k.toString(),v)); data.put("daily",response.get("daily")); data.put("timezone",response.get("timezone")); data.put("source","Open-Meteo"); data.put("observedAt",Instant.now().toString());
        if(cache.size()>500)cache.clear(); cache.put(key,new Cached(Instant.now(),data));return data;
    }

    private Map<?,?> providerData(java.net.URI uri, String operation) {
        try {
            Map<?,?> data = client.get().uri(uri).retrieve().body(Map.class);
            if (data != null) return data;
            log.warn("Weather provider failure: operation={} category=EMPTY_RESPONSE", operation);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Il servizio meteo ha restituito una risposta vuota. Riprova più tardi.");
        } catch (RestClientResponseException error) {
            int status = error.getStatusCode().value();
            // Never log the URL, provider body or exception message: they may contain an API key.
            log.warn("Weather provider failure: operation={} category=HTTP upstreamStatus={}", operation, status);
            String message = status == 429
                    ? "Limite di richieste del servizio meteo raggiunto. Riprova tra qualche minuto."
                    : status == 401 || status == 403
                    ? "Il provider meteo ha rifiutato l'accesso. Verificare endpoint e credenziali del servizio."
                    : "Il servizio meteo esterno non è disponibile (HTTP " + status + "). Riprova più tardi.";
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
        } catch (ResourceAccessException error) {
            Throwable cause = error;
            String category = "NETWORK";
            while (true) {
                if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) category = "TIMEOUT";
                else if (cause instanceof java.net.UnknownHostException || cause instanceof java.nio.channels.UnresolvedAddressException) category = "DNS";
                else if (cause instanceof javax.net.ssl.SSLException) category = "TLS";
                if (cause.getCause() == null || cause.getCause() == cause) break;
                cause = cause.getCause();
            }
            log.warn("Weather provider failure: operation={} category={} causeType={}", operation, category, cause.getClass().getSimpleName());
            String message = category.equals("TIMEOUT")
                    ? "Il servizio meteo non ha risposto in tempo. Riprova più tardi."
                    : "Il server non riesce a raggiungere il servizio meteo. Verificare la connessione al provider.";
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
        } catch (RestClientException error) {
            log.warn("Weather provider failure: operation={} category=INVALID_RESPONSE causeType={}", operation, error.getClass().getSimpleName());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Il servizio meteo ha restituito dati non validi. Riprova più tardi.");
        }
    }
}
