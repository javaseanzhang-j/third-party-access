package com.ftk.tpip.worker.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationDispatcherTest {
    private HttpServer control;
    private HttpServer webhook;
    private final ObjectMapper json = JsonMapper.builder().findAndAddModules().build();
    private final AtomicReference<String> completionPath = new AtomicReference<>();
    private final AtomicReference<String> completionBody = new AtomicReference<>();
    private final AtomicReference<String> webhookBody = new AtomicReference<>();
    private final AtomicReference<String> idempotencyKey = new AtomicReference<>();
    private final AtomicInteger webhookStatus = new AtomicInteger(204);
    private boolean frozenMessage;
    private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();

    @BeforeEach
    void startServers() throws IOException {
        control = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        webhook = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        control.createContext("/internal/v1/notification-deliveries:claim", exchange -> respond(exchange, 200,
                "[" + taskJson("CLAIMED") + "]"));
        control.createContext("/internal/v1/notification-deliveries/7:delivered", this::captureCompletion);
        control.createContext("/internal/v1/notification-deliveries/7:failed", this::captureCompletion);
        webhook.createContext("/events", exchange -> {
            webhookBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotencyKey.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            if (webhookStatus.get() == 429) exchange.getResponseHeaders().set("Retry-After", "45");
            respond(exchange, webhookStatus.get(), "");
        });
        control.start();
        webhook.start();
    }

    @AfterEach
    void stopServers() {
        control.stop(0);
        webhook.stop(0);
    }

    @Test
    void deliversWebhookWithStableIdempotencyAndAcknowledgesControlPlane() throws Exception {
        dispatcher().dispatch();

        assertEquals("tpip-outbox-70-channel-ops-primary", idempotencyKey.get());
        assertEquals("/internal/v1/notification-deliveries/7:delivered", completionPath.get());
        assertEquals("worker-test-1", json.readTree(completionBody.get()).path("workerId").asText());
        assertEquals(70, json.readTree(webhookBody.get()).path("eventId").asLong());
        assertEquals("ops-primary", json.readTree(webhookBody.get()).path("channelCode").asText());
        assertEquals("TPIP_HEALTH_ALERT_OPENED",
                json.readTree(webhookBody.get()).path("eventType").asText());
        assertEquals("WARNING", json.readTree(webhookBody.get()).path("payload").path("severity").asText());
        assertEquals(1.0, metrics.get("tpip.notification.deliveries")
                .tags("channel", "ops-primary", "outcome", "success", "code", "DELIVERED")
                .counter().count());
    }

    @Test
    void reportsOnlySafeFailureCodeWhenWebhookRejectsDelivery() throws Exception {
        webhookStatus.set(503);

        dispatcher().dispatch();

        assertEquals("/internal/v1/notification-deliveries/7:failed", completionPath.get());
        var body = json.readTree(completionBody.get());
        assertEquals("worker-test-1", body.path("workerId").asText());
        assertEquals("WEBHOOK_HTTP_503", body.path("error").asText());
        assertTrue(!completionBody.get().contains("127.0.0.1"));
    }

    @Test
    void propagatesRetryAfterAsGovernedSecondsWithoutLeakingHeaders() throws Exception {
        webhookStatus.set(429);

        dispatcher().dispatch();

        var body = json.readTree(completionBody.get());
        assertEquals("WEBHOOK_HTTP_429", body.path("error").asText());
        assertEquals(45, body.path("retryAfterSeconds").asLong());
        assertTrue(!completionBody.get().contains("Retry-After"));
    }

    @Test
    void deliversFrozenTemplatePayloadWithoutRebuildingEnvelope() throws Exception {
        frozenMessage = true;

        dispatcher().dispatch();

        var body = json.readTree(webhookBody.get());
        assertEquals("Health warning", body.path("title").asText());
        assertEquals("WARNING", body.path("level").asText());
        assertTrue(body.path("eventId").isMissingNode());
    }

    @Test
    void reportsCircuitLeaseWithoutCallingEndpoint() throws Exception {
        NotificationEndpointCircuitBreaker circuit = new NotificationEndpointCircuitBreaker() {
            @Override public void beforeDelivery(NotificationTask task) {
                throw new NotificationDeliveryException("ENDPOINT_CIRCUIT_OPEN", Duration.ofSeconds(12));
            }
            @Override public void recordSuccess(NotificationTask task) {}
            @Override public void recordFailure(NotificationTask task, String errorCode) {}
        };

        dispatcher(circuit).dispatch();

        var report = json.readTree(completionBody.get());
        assertEquals("ENDPOINT_CIRCUIT_OPEN", report.path("error").asText());
        assertEquals(12, report.path("retryAfterSeconds").asLong());
        assertEquals(null, webhookBody.get());
    }

    private NotificationDispatcher dispatcher() {
        return dispatcher(null);
    }

    private NotificationDispatcher dispatcher(NotificationEndpointCircuitBreaker circuit) {
        NotificationDispatcherProperties properties = new NotificationDispatcherProperties();
        properties.setEnabled(true);
        properties.setAutomationToken("0123456789abcdef-test");
        properties.setWorkerId("worker-test-1");
        properties.setControlPlaneBaseUri(URI.create("http://127.0.0.1:" + control.getAddress().getPort()));
        properties.setAllowHttpWebhooks(true);
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.validate();
        HttpClient http = HttpClient.newHttpClient();
        var controlClient = new ControlPlaneNotificationClient(http, json, properties);
        if (circuit != null) return new NotificationDispatcher(controlClient,
                List.of(new WebhookNotificationProvider(http, json, properties, reference -> "test-token")), properties,
                new NotificationDispatcherMetrics(metrics), circuit);
        return new NotificationDispatcher(controlClient,
                List.of(new WebhookNotificationProvider(http, json, properties, reference -> "test-token")), properties,
                new NotificationDispatcherMetrics(metrics));
    }

    private void captureCompletion(HttpExchange exchange) throws IOException {
        completionPath.set(exchange.getRequestURI().getPath());
        completionBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        respond(exchange, 200, taskJson(exchange.getRequestURI().getPath().endsWith(":delivered")
                ? "DELIVERED" : "PENDING"));
    }

    private String taskJson(String status) {
        return "{\"id\":7,\"eventId\":70,\"channelCode\":\"ops-primary\","
                + "\"channelVersionId\":3,\"providerType\":\"WEBHOOK\","
                + "\"endpointUri\":\"http://127.0.0.1:" + webhook.getAddress().getPort() + "/events\","
                + "\"authorizationSecretRef\":null,"
                + (frozenMessage ? "\"templateVersionId\":9,\"messageContentType\":\"application/json\",\"messagePayload\":{\"title\":\"Health warning\",\"level\":\"WARNING\"}," : "")
                + "\"eventType\":\"TPIP_HEALTH_ALERT_OPENED\","
                + "\"aggregateType\":\"DEPLOYMENT_HEALTH_ALERT\",\"aggregateId\":\"19\","
                + "\"payload\":{\"severity\":\"WARNING\"},\"status\":\"" + status + "\","
                + "\"attemptCount\":1,\"availableAt\":\"2026-08-08T10:00:00Z\","
                + "\"claimedBy\":\"worker-test-1\",\"claimedAt\":\"2026-08-08T10:00:00Z\","
                + "\"deliveredAt\":null,\"lastError\":null,"
                + "\"createdAt\":\"2026-08-08T09:59:00Z\",\"updatedAt\":\"2026-08-08T10:00:00Z\"}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
