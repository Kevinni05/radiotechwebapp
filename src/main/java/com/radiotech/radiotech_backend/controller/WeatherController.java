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
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.met-endpoint:https://api.met.no/weatherapi/locationforecast/2.0/compact}") private String endpoint;
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.api-key:}") private String apiKey;
    public WeatherController() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(8)); client = RestClient.builder().requestFactory(factory).build();
    }
    WeatherController(RestClient client) { this.client = client; }
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.location-endpoint:https://photon.komoot.io/api/}") private String geocodingEndpoint;
    @GetMapping("/locations") public Map<String,Object> locations(@RequestParam String query) {
        String name=query.strip();
        if(name.length()<2||name.length()>100)throw new IllegalArgumentException("Inserisci una località da 2 a 100 caratteri.");
        String key="location:"+name.toLowerCase(Locale.ROOT);
        var entry=cache.get(key); if(entry!=null&&entry.at().plusSeconds(3600).isAfter(Instant.now()))return entry.data();
        var uri=org.springframework.web.util.UriComponentsBuilder.fromUriString(geocodingEndpoint).queryParam("q",name).queryParam("limit",8);
        Map<?,?> response=providerData(uri.build().encode().toUri(), "locations");
        List<Map<String,Object>> results=new ArrayList<>();
        if(response.get("features") instanceof List<?> features) for(Object item:features) {
            var feature=map(item); var properties=map(feature.get("properties")); Object coords=map(feature.get("geometry")).get("coordinates");
            if(coords instanceof List<?> c && c.size()>=2) { Map<String,Object> place=new LinkedHashMap<>();place.put("longitude",c.get(0));place.put("latitude",c.get(1));place.put("name",properties.get("name"));place.put("admin1",properties.get("state"));place.put("country",properties.get("country"));results.add(place); }
        }
        Map<String,Object> data=new LinkedHashMap<>(); data.put("results",results);
        if(cache.size()>500)cache.clear(); cache.put(key,new Cached(Instant.now(),data)); return data;
    }
    @org.springframework.beans.factory.annotation.Value("${radiotech.weather.user-agent:RadioTech/1.0 https://github.com/Kevinni05/radiotechwebapp}") private String userAgent = "RadioTech/1.0 https://github.com/Kevinni05/radiotechwebapp";
    private final Map<String, Instant> retryAt = new ConcurrentHashMap<>();
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    @GetMapping public Map<String,Object> current(@RequestParam double latitude, @RequestParam double longitude,
            @RequestParam(defaultValue="Europe/Rome") String timezone) {
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||latitude < -90||latitude>90||longitude < -180||longitude>180) throw new IllegalArgumentException("Coordinate meteo non valide.");
        ZoneId zone;
        try { zone = ZoneId.of(timezone); } catch (Exception e) { throw new IllegalArgumentException("Fuso orario non valido."); }
        String lat = String.format(Locale.ROOT,"%.4f",latitude), lon = String.format(Locale.ROOT,"%.4f",longitude);
        String key = lat+","+lon+":"+zone;
        // Fixed lock stripes keep memory bounded while coalescing identical concurrent requests.
        Object lock = locks.computeIfAbsent(Integer.toString(Math.floorMod(key.hashCode(),64)), k -> new Object());
        synchronized(lock) {
            Instant now = Instant.now(); var entry = cache.get(key);
            if(entry!=null && entry.at().isAfter(now)) return entry.data();
            if(retryAt.getOrDefault(key,Instant.EPOCH).isAfter(now)) return stale(entry);
            try {
                var uri = org.springframework.web.util.UriComponentsBuilder.fromUriString(endpoint).queryParam("lat",lat).queryParam("lon",lon).build().encode().toUri();
                var request = client.get().uri(uri).header("User-Agent", userAgent);
                if(entry!=null && entry.data().get("lastModified") instanceof String modified) request.header("If-Modified-Since",modified);
                var response = request.retrieve().toEntity(Map.class);
                Instant expires = now.plusSeconds(900);
                try { Instant providerExpires = ZonedDateTime.parse(response.getHeaders().getFirst("Expires"),java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant(); if(providerExpires.isAfter(expires)) expires=providerExpires; } catch(Exception ignored) {}
                if(response.getStatusCode().value()==304 && entry!=null) { cache.put(key,new Cached(expires,entry.data())); return entry.data(); }
                Map<String,Object> data = normalizeForecast(response.getBody(),zone);
                data.put("lastModified",response.getHeaders().getFirst("Last-Modified"));
                if(cache.size()>500) { cache.entrySet().removeIf(e -> e.getValue().at().plusSeconds(21600).isBefore(now)); if(cache.size()>500) cache.clear(); }
                retryAt.remove(key); cache.put(key,new Cached(expires,data)); return data;
            } catch(RestClientResponseException error) {
                int status=error.getStatusCode().value();
                log.warn("Weather provider failure: operation=forecast category=HTTP upstreamStatus={}",status);
                long delay=300; String retry=error.getResponseHeaders()==null?null:error.getResponseHeaders().getFirst("Retry-After");
                try { delay=Math.max(300,Long.parseLong(retry)); } catch(Exception ignored) { try { delay=Math.max(300,Duration.between(now,ZonedDateTime.parse(retry,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds()); } catch(Exception ignoredDate) {} }
                if(retryAt.size()>500) retryAt.entrySet().removeIf(e -> !e.getValue().isAfter(now));
                retryAt.put(key,now.plusSeconds(Math.min(delay,86400)));
                if(entry!=null) return stale(entry);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,status==429?"Limite di richieste del servizio meteo raggiunto. Riprova tra qualche minuto.":status==401||status==403?"Il provider meteo ha rifiutato l'accesso. Verificare l'identificazione del servizio.":"Il servizio meteo esterno non è disponibile (HTTP "+status+"). Riprova più tardi.");
            } catch(RestClientException error) {
                retryAt.put(key,now.plusSeconds(300));
                log.warn("Weather provider failure: operation=forecast category=TIMEOUT_OR_NETWORK causeType={}",error.getClass().getSimpleName());
                if(entry!=null)return stale(entry);
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Il servizio meteo non ha risposto in tempo o ha restituito dati non validi. Riprova più tardi.");
            }
        }
    }
    // Compatibility overload for internal callers and existing tests.
    public Map<String,Object> current(double latitude,double longitude) { return current(latitude,longitude,"Europe/Rome"); }
    private Map<String,Object> stale(Cached entry) {
        if(entry==null || entry.at().plusSeconds(21600).isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Limite di richieste o servizio meteo non disponibile. Riprova tra qualche minuto.");
        var data=new LinkedHashMap<>(entry.data()); data.put("stale",true); return data;
    }
    private static Map<?,?> map(Object value) { return value instanceof Map<?,?> m ? m : Map.of(); }
    private static int weatherCode(String symbol) {
        if(symbol.contains("thunder"))return 95; if(symbol.contains("snow")||symbol.contains("sleet"))return 73;
        if(symbol.contains("rainshowers"))return 81; if(symbol.contains("rain"))return 63; if(symbol.contains("fog"))return 45;
        if(symbol.startsWith("partlycloudy"))return 2; if(symbol.startsWith("cloudy"))return 3; if(symbol.startsWith("fair"))return 1; return symbol.startsWith("clearsky")?0:3;
    }
    static Map<String,Object> normalizeForecast(Map<?,?> response,ZoneId zone) {
        Object series=map(response==null?null:response.get("properties")).get("timeseries");
        if(!(series instanceof List<?> rows)||rows.isEmpty())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Il servizio meteo ha restituito dati non validi.");
        Map<String,Object> result=new LinkedHashMap<>(); Map<?,?> first=map(rows.getFirst()),data=map(first.get("data")),instant=map(map(data.get("instant")).get("details"));
        if(!(instant.get("air_temperature") instanceof Number))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Il servizio meteo ha restituito dati non validi.");
        result.put("temperature_2m",instant.get("air_temperature")); result.put("relative_humidity_2m",instant.get("relative_humidity"));
        result.put("wind_speed_10m",instant.get("wind_speed") instanceof Number n?n.doubleValue()*3.6:null);
        result.put("weather_code",code(data)); result.put("timezone",zone.toString()); result.put("source","MET Norway");
        result.put("observedAt",first.get("time")); result.put("stale",false);
        // Aggregate actual forecast samples in the requested timezone; never invent unavailable UV/sunrise/probabilities.
        Map<LocalDate,List<Map<?,?>>> days=new TreeMap<>();
        for(Object row:rows) {var r=map(row); LocalDate day=Instant.parse(r.get("time").toString()).atZone(zone).toLocalDate();days.computeIfAbsent(day,k->new ArrayList<>()).add(r);}
        Map<String,Object> daily=new LinkedHashMap<>(); for(String k:List.of("time","weather_code","temperature_2m_min","temperature_2m_max","precipitation_probability_max"))daily.put(k,new ArrayList<>());
        for(var day:days.entrySet().stream().limit(6).toList()) {
            var temperatures=day.getValue().stream().map(r->map(map(map(r.get("data")).get("instant")).get("details")).get("air_temperature")).filter(Number.class::isInstance).map(Number.class::cast).mapToDouble(Number::doubleValue).summaryStatistics();
            if(temperatures.getCount()==0)continue;
            Map<?,?> representative=day.getValue().stream().min(Comparator.comparingInt(r->Math.abs(Instant.parse(r.get("time").toString()).atZone(zone).getHour()-12))).orElseThrow();
            add(daily,"time",day.getKey().toString()); add(daily,"weather_code",code(map(representative.get("data")))); add(daily,"temperature_2m_min",temperatures.getMin()); add(daily,"temperature_2m_max",temperatures.getMax());
            Object probability=map(map(map(representative.get("data")).get("next_6_hours")).get("details")).get("probability_of_precipitation"); add(daily,"precipitation_probability_max",probability);
        }
        result.put("daily",daily);return result;
    }
    @SuppressWarnings("unchecked") private static void add(Map<String,Object> daily,String key,Object value){((List<Object>)daily.get(key)).add(value);}
    private static int code(Map<?,?> data) { for(String k:List.of("next_1_hours","next_6_hours","next_12_hours")){Object symbol=map(map(data.get(k)).get("summary")).get("symbol_code");if(symbol!=null)return weatherCode(symbol.toString());}return 3; }

    private Map<?,?> providerData(java.net.URI uri, String operation) {
        try {
            Map<?,?> data = client.get().uri(uri).header("User-Agent",userAgent).retrieve().body(Map.class);
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
