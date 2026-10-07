package com.radiotech.radiotech_backend.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/v1/weather")
public class WeatherController {
    private record Cached(Instant at, Map<String,Object> data) {}
    private final Map<String,Cached> cache = new ConcurrentHashMap<>();
    private final RestClient client;
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.endpoint:https://api.open-meteo.com/v1/forecast}") private String endpoint;
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.api-key:}") private String apiKey;
    public WeatherController() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(8)); client = RestClient.builder().requestFactory(factory).build();
    }
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.geocoding-endpoint:https://geocoding-api.open-meteo.com/v1/search}") private String geocodingEndpoint;
    @GetMapping("/locations") public Map<String,Object> locations(@RequestParam String query) {
        String name=query.strip();
        if(name.length()<2||name.length()>100)throw new IllegalArgumentException("Inserisci una località da 2 a 100 caratteri.");
        String key="location:"+name.toLowerCase(Locale.ROOT);
        var entry=cache.get(key); if(entry!=null&&entry.at().plusSeconds(3600).isAfter(Instant.now()))return entry.data();
        var uri=org.springframework.web.util.UriComponentsBuilder.fromUriString(geocodingEndpoint).queryParam("name",name).queryParam("count",8).queryParam("language","it").queryParam("format","json");
        Map<?,?> response=client.get().uri(uri.build().encode().toUri()).retrieve().body(Map.class);
        if(response==null)throw new IllegalStateException("Ricerca località temporaneamente non disponibile.");
        Map<String,Object> data=new LinkedHashMap<>(); data.put("results",response.get("results") instanceof List<?> results?results:List.of());
        if(cache.size()>500)cache.clear(); cache.put(key,new Cached(Instant.now(),data)); return data;
    }
    @GetMapping public Map<String,Object> current(@RequestParam double latitude, @RequestParam double longitude) {
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||latitude < -90||latitude>90||longitude < -180||longitude>180) throw new IllegalArgumentException("Coordinate meteo non valide.");
        String key = String.format(Locale.ROOT,"%.2f,%.2f",latitude,longitude);
        var entry = cache.get(key); if(entry!=null && entry.at().plusSeconds(600).isAfter(Instant.now()))return entry.data();
        var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(endpoint).queryParam("latitude",latitude).queryParam("longitude",longitude).queryParam("current","temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m").queryParam("daily","weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset,uv_index_max").queryParam("forecast_days",6).queryParam("timezone","auto");
        if(apiKey!=null&&!apiKey.isBlank())uri.queryParam("apikey",apiKey);
        Map<?,?> response = client.get().uri(uri.build().encode().toUri()).retrieve().body(Map.class);
        if(response==null||!(response.get("current") instanceof Map<?,?> current))throw new IllegalStateException("Meteo temporaneamente non disponibile.");
        Map<String,Object> data=new LinkedHashMap<>(); current.forEach((k,v)->data.put(k.toString(),v)); data.put("daily",response.get("daily")); data.put("timezone",response.get("timezone")); data.put("source","Open-Meteo"); data.put("observedAt",Instant.now().toString());
        if(cache.size()>500)cache.clear(); cache.put(key,new Cached(Instant.now(),data));return data;
    }
}
