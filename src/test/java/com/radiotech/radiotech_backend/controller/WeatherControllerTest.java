package com.radiotech.radiotech_backend.controller;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class WeatherControllerTest {
 @Test void forecastsAndSearchAreCachedAndValidated() throws Exception {
  var count=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/forecast",exchange->{count.incrementAndGet();String query=exchange.getRequestURI().getQuery();assertTrue(query.contains("forecast_days=6"));assertTrue(query.contains("relative_humidity_2m"));assertTrue(query.contains("precipitation_probability_max"));byte[] body="{\"current\":{\"temperature_2m\":24},\"daily\":{\"time\":[\"2026-10-08\"]},\"timezone\":\"Europe/Rome\"}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
  server.createContext("/search",exchange->{count.incrementAndGet();byte[] body="{\"results\":[{\"name\":\"Lecce\",\"latitude\":40.35,\"longitude\":18.17}]}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try {var c=new WeatherController();String base="http://127.0.0.1:"+server.getAddress().getPort();ReflectionTestUtils.setField(c,"endpoint",base+"/forecast");ReflectionTestUtils.setField(c,"geocodingEndpoint",base+"/search");var w=c.current(41,16);assertEquals("Europe/Rome",w.get("timezone"));assertNotNull(w.get("daily"));assertEquals(w,c.current(41,16));assertEquals(1,((List<?>)c.locations("Lecce").get("results")).size());c.locations("Lecce");assertEquals(2,count.get());assertThrows(IllegalArgumentException.class,()->c.current(91,16));assertThrows(IllegalArgumentException.class,()->c.locations("a"));}finally{server.stop(0);}
 }
}
