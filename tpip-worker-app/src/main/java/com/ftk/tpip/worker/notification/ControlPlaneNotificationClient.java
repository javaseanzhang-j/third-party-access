package com.ftk.tpip.worker.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

final class ControlPlaneNotificationClient {
    private static final TypeReference<List<NotificationTask>> TASKS = new TypeReference<>() {};
    private final HttpClient http;
    private final ObjectMapper json;
    private final NotificationDispatcherProperties properties;

    ControlPlaneNotificationClient(HttpClient http, ObjectMapper json, NotificationDispatcherProperties properties) {
        this.http = http;
        this.json = json;
        this.properties = properties;
    }

    List<NotificationTask> claim() {
        return send(":claim", new ClaimRequest(properties.getWorkerId(), properties.getBatchSize()), TASKS);
    }

    void delivered(long id) {
        send("/" + id + ":delivered", new WorkerRequest(properties.getWorkerId()),
                new TypeReference<NotificationTask>() {});
    }

    void failed(long id, String errorCode, java.time.Duration retryAfter) {
        Long seconds = retryAfter == null ? null : Math.max(0, (retryAfter.toMillis() + 999) / 1000);
        send("/" + id + ":failed", new FailureRequest(properties.getWorkerId(), errorCode, seconds),
                new TypeReference<NotificationTask>() {});
    }

    private <T> T send(String suffix, Object body, TypeReference<T> type) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri(suffix)).timeout(properties.getRequestTimeout())
                    .header("Authorization", "Bearer " + properties.getAutomationToken().trim())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "tpip-notification-worker/0.1")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(json.writeValueAsBytes(body))).build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NotificationDeliveryException("CONTROL_PLANE_HTTP_" + response.statusCode());
            }
            return json.readValue(response.body(), type);
        } catch (IOException exception) {
            throw new NotificationDeliveryException("CONTROL_PLANE_IO_FAILURE", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NotificationDeliveryException("CONTROL_PLANE_INTERRUPTED", exception);
        }
    }

    private URI uri(String suffix) {
        String base = properties.getControlPlaneBaseUri().toString();
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return URI.create(base + "/internal/v1/notification-deliveries" + suffix);
    }

    private record ClaimRequest(String workerId, int batchSize) {}
    private record WorkerRequest(String workerId) {}
    private record FailureRequest(String workerId, String error, Long retryAfterSeconds) {}
}
