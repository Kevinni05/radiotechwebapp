import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;

/** Passive Render edge: no Firebase credentials, signing keys, user database or administrative API. */
public final class EdgeProxy {
    private static final Set<String> HOP = Set.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
        "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length", "expect");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    public static void main(String[] args) throws Exception {
        URI upstream = URI.create(System.getenv("RADIOTECH_UPSTREAM_URL"));
        if (!"https".equals(upstream.getScheme()) || upstream.getHost() == null || !upstream.getHost().endsWith(".run.app")
            || upstream.getUserInfo() != null || upstream.getQuery() != null || upstream.getFragment() != null
            || !(upstream.getPath().isEmpty() || upstream.getPath().equals("/"))) throw new IllegalArgumentException("Explicit HTTPS Cloud Run upstream required");
        String base = upstream.toString().replaceAll("/$", "");
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"))), 128);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", exchange -> proxy(exchange, base));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(5)));
        server.start();
        System.out.println("RadioTech edge listening on " + server.getAddress());
    }

    private static void proxy(HttpExchange exchange, String base) throws IOException {
        try {
            String path = exchange.getRequestURI().getRawPath();
            if (path == null || !path.startsWith("/")) throw new IOException("Invalid path");
            String query = exchange.getRequestURI().getRawQuery();
            URI target = URI.create(base + path + (query == null ? "" : "?" + query));
            var request = HttpRequest.newBuilder(target).timeout(Duration.ofSeconds(180));
            Set<String> blocked = new HashSet<>(HOP);
            for (String connection : exchange.getRequestHeaders().getOrDefault("Connection", List.of()))
                for (String header : connection.split(",")) blocked.add(header.trim().toLowerCase(Locale.ROOT));
            exchange.getRequestHeaders().forEach((key, values) -> {
                String lower = key.toLowerCase(Locale.ROOT);
                if (!blocked.contains(lower) && !lower.startsWith("x-forwarded-") && !lower.startsWith("x-identity-")
                    && !lower.startsWith("x-goog-") && !lower.equals("forwarded") && !lower.equals("x-serverless-authorization"))
                    for (String value : values) request.header(key, value);
            });
            request.header("X-Forwarded-Proto", "https");
            String publicHost = System.getenv("RENDER_EXTERNAL_HOSTNAME");
            if (publicHost != null && publicHost.matches("[a-zA-Z0-9.-]+")) request.header("X-Forwarded-Host", publicHost);
            String length = exchange.getRequestHeaders().getFirst("Content-Length");
            if (length != null && Long.parseLong(length) > 64L * 1024 * 1024) {
                exchange.sendResponseHeaders(413, -1); exchange.close(); return;
            }
            boolean body = !Set.of("GET", "HEAD").contains(exchange.getRequestMethod());
            request.method(exchange.getRequestMethod(), body ? HttpRequest.BodyPublishers.ofInputStream(() -> new Bounded(exchange.getRequestBody()))
                : HttpRequest.BodyPublishers.noBody());
            var response = HTTP.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
            response.headers().map().forEach((key, values) -> { if (!HOP.contains(key.toLowerCase(Locale.ROOT))) exchange.getResponseHeaders().put(key, values); });
            boolean empty = exchange.getRequestMethod().equals("HEAD") || response.statusCode() == 204 || response.statusCode() == 304;
            exchange.sendResponseHeaders(response.statusCode(), empty ? -1 : 0);
            try (InputStream input = response.body(); OutputStream output = exchange.getResponseBody()) { if (!empty) input.transferTo(output); }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt(); fail(exchange);
        } catch (Exception error) {
            System.err.println("RadioTech edge request failed: " + error.getClass().getSimpleName());
            fail(exchange);
        }
        finally { exchange.close(); }
    }
    private static void fail(HttpExchange exchange) throws IOException {
        if (exchange.getResponseCode() != -1) return;
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        byte[] message = "{\"message\":\"Centro operativo temporaneamente non disponibile. Riprova.\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(502, message.length); exchange.getResponseBody().write(message);
    }
    private static final class Bounded extends FilterInputStream {
        private long remaining = 64L * 1024 * 1024;
        Bounded(InputStream input) { super(input); }
        public int read() throws IOException { int value = super.read(); if (value != -1 && --remaining < 0) throw new IOException("Body too large"); return value; }
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int result = in.read(buffer, offset, (int)Math.min(length, Math.max(1, remaining + 1)));
            if (result > 0 && (remaining -= result) < 0) throw new IOException("Body too large"); return result;
        }
    }
}
