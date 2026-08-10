package com.ftk.tpip.worker.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DeploymentHealthSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-08-08T10:07:30Z");
    private HttpServer control;
    private HttpServer prometheus;
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<String> evaluationBody = new AtomicReference<>();

    @BeforeEach
    void startServers() throws IOException {
        control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        prometheus = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        control.createContext("/internal/v1/deployment-health/candidates", exchange -> respond(exchange, 200,
                "[{\"deploymentId\":42,\"deploymentCode\":\"customer.lookup.canary\",\"operationId\":1,"
                        + "\"environmentCode\":\"test\",\"trafficPercentage\":\"10.00\","
                        + "\"activatedAt\":\"2026-08-08T09:50:00Z\"}]"));
        control.createContext("/internal/v1/deployment-health/42:evaluate", exchange -> {
            evaluationBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"id\":9,\"deploymentId\":42,\"decision\":\"UNHEALTHY\","
                    + "\"action\":\"AUTO_ROLLBACK\",\"rollbackDeploymentId\":43,"
                    + "\"windowStart\":\"2026-08-08T10:00:00Z\","
                    + "\"windowEnd\":\"2026-08-08T10:05:00Z\",\"evidence\":{}}" );
        });
        prometheus.createContext("/api/v1/query", exchange -> {
            String query = URLDecoder.decode(exchange.getRequestURI().getRawQuery(), StandardCharsets.UTF_8);
            String value = query.contains("histogram_quantile") ? "0.42"
                    : query.contains("outcome!=") ? "7" : "120";
            respond(exchange, 200, "{\"status\":\"success\",\"data\":{\"resultType\":\"vector\","
                    + "\"result\":[{\"metric\":{},\"value\":[1,\"" + value + "\"]}]}}" );
        });
        control.start();
        prometheus.start();
    }

    @AfterEach
    void stopServers() {
        control.stop(0);
        prometheus.stop(0);
    }

    @Test
    void collectsAlignedPrometheusEvidenceAndSubmitsOneGovernedEvaluation() throws Exception {
        HealthWorkerProperties properties = properties();
        HttpClient http = HttpClient.newHttpClient();
        var controlClient = new ControlPlaneHealthClient(http, json, properties);
        var prometheusClient = new PrometheusHealthEvidenceClient(http, json, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        FakeGuard guard = new FakeGuard(true);
        var scheduler = new DeploymentHealthScheduler(controlClient, prometheusClient, guard, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        scheduler.evaluateActiveCanaries();

        var submitted = json.readTree(evaluationBody.get());
        assertEquals("2026-08-08T10:00:00Z", submitted.path("windowStart").asText());
        assertEquals("2026-08-08T10:05:00Z", submitted.path("windowEnd").asText());
        assertEquals(120, submitted.path("sampleCount").asLong());
        assertEquals(7, submitted.path("failureCount").asLong());
        assertEquals(420, submitted.path("p95LatencyMs").asLong());
        assertEquals("prometheus", submitted.path("evidence").path("source").asText());
        assertEquals(1, guard.completed);
        assertEquals(0, guard.released);
    }

    @Test
    void skipsCollectionWhenAnotherWorkerOwnsTheWindow() {
        HealthWorkerProperties properties = properties();
        HttpClient http = HttpClient.newHttpClient();
        FakeGuard guard = new FakeGuard(false);
        var scheduler = new DeploymentHealthScheduler(new ControlPlaneHealthClient(http, json, properties),
                new PrometheusHealthEvidenceClient(http, json, properties, Clock.fixed(NOW, ZoneOffset.UTC)),
                guard, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        scheduler.evaluateActiveCanaries();
        assertTrue(evaluationBody.get() == null);
        assertEquals(0, guard.completed);
    }

    private HealthWorkerProperties properties() {
        var value = new HealthWorkerProperties();
        value.setEnabled(true);
        value.setAutomationToken("0123456789abcdef-test");
        value.setControlPlaneBaseUri(URI.create("http://127.0.0.1:" + control.getAddress().getPort()));
        value.setPrometheusBaseUri(URI.create("http://127.0.0.1:" + prometheus.getAddress().getPort()));
        value.setEvaluationWindow(Duration.ofMinutes(5));
        return value;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static final class FakeGuard implements HealthWindowGuard {
        private final boolean acquired;
        private int completed;
        private int released;
        private FakeGuard(boolean acquired) { this.acquired = acquired; }
        @Override public boolean tryAcquire(long deploymentId, Instant windowEnd) { return acquired; }
        @Override public void complete(long deploymentId) { completed++; }
        @Override public void release(long deploymentId, Instant windowEnd) { released++; }
    }
}
