package com.radiotech.radiotech_backend.controller;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class WeatherControllerTest {
 static String forecast() {return "{\"properties\":{\"timeseries\":[{\"time\":\"2026-10-09T23:00:00Z\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":24,\"relative_humidity\":60,\"wind_speed\":2}},\"next_1_hours\":{\"summary\":{\"symbol_code\":\"clearsky_night\"}}}},{\"time\":\"2026-10-10T12:00:00Z\",\"data\":{\"instant\":{\"details\":{\"air_temperature\":28}},\"next_6_hours\":{\"summary\":{\"symbol_code\":\"rain\"}}}}]}}";}
 @Test void forecastsAndSearchAreCachedAndValidated() throws Exception {
  var count=new AtomicInteger();var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/forecast",exchange->{count.incrementAndGet();String query=exchange.getRequestURI().getQuery();assertTrue(query.contains("lat=41.0000"));assertTrue(query.contains("lon=16.0000"));assertTrue(exchange.getRequestHeaders().getFirst("User-Agent").contains("RadioTech"));byte[] body=forecast().getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});
  server.createContext("/search",exchange->{count.incrementAndGet();byte[] body="{\"features\":[{\"properties\":{\"name\":\"Lecce\"},\"geometry\":{\"coordinates\":[18.17,40.35]}}]}".getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();
  try {var c=new WeatherController();String base="http://127.0.0.1:"+server.getAddress().getPort();ReflectionTestUtils.setField(c,"endpoint",base+"/forecast");ReflectionTestUtils.setField(c,"geocodingEndpoint",base+"/search");
   try(var pool=Executors.newFixedThreadPool(8)){var futures=new ArrayList<Future<Map<String,Object>>>();for(int i=0;i<8;i++)futures.add(pool.submit(()->c.current(41,16)));for(var future:futures)assertEquals("MET Norway",future.get().get("source"));}
   var w=c.current(41,16);assertEquals("Europe/Rome",w.get("timezone"));assertEquals(7.2,(Double)w.get("wind_speed_10m"),.001);var daily=(Map<?,?>)w.get("daily");assertEquals(List.of("2026-10-10"),daily.get("time"));assertEquals(List.of(24.0),daily.get("temperature_2m_min"));assertEquals(List.of(28.0),daily.get("temperature_2m_max"));
   assertEquals(1,((List<?>)c.locations("Lecce").get("results")).size());c.locations("Lecce");assertEquals(2,count.get());assertThrows(IllegalArgumentException.class,()->c.current(91,16));assertThrows(IllegalArgumentException.class,()->c.locations("a"));assertThrows(IllegalArgumentException.class,()->c.current(41,16,"bad"));
  }finally{server.stop(0);}
 }
}
