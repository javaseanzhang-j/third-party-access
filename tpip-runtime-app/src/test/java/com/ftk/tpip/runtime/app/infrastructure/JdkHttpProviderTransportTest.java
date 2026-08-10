package com.ftk.tpip.runtime.app.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.adapters.runtime.JdkHttpProviderTransport;
import com.ftk.tpip.runtime.ProviderTransportRequest;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class JdkHttpProviderTransportTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void exchangesJsonWithoutFollowingRedirects() throws Exception {
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/provider", exchange -> {
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"provider_id\":\"P-1\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        var transport = new JdkHttpProviderTransport(json, 4096);
        var response = transport.exchange(request("/provider", Map.of("X-Request-Id", List.of("req-1"))));

        assertEquals(200, response.statusCode());
        assertEquals("P-1", response.body().path("provider_id").asText());
        assertEquals("req-1", requestId.get());
        assertEquals("C-1", json.readTree(requestBody.get()).path("customer_id").asText());
    }

    @Test
    void blocksHopByHopAndAuthorityHeaders() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        var transport = new JdkHttpProviderTransport(json, 4096);
        assertThrows(IllegalStateException.class,
                () -> transport.exchange(request("/provider", Map.of("Host", List.of("attacker.example")))));
    }

    private ProviderTransportRequest request(String path, Map<String, List<String>> headers) {
        URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
        return new ProviderTransportRequest(uri, "POST", headers,
                json.createObjectNode().put("customer_id", "C-1"), Duration.ofSeconds(1),
                Duration.ofSeconds(2), Duration.ofSeconds(3));
    }
}
