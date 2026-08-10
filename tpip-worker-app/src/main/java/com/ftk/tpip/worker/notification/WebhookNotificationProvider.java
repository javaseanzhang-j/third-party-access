package com.ftk.tpip.worker.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.time.Clock;

final class WebhookNotificationProvider implements NotificationProvider {
    private final HttpClient http;
    private final ObjectMapper json;
    private final NotificationDispatcherProperties properties;
    private final NotificationSecretResolver secrets;
    private final Clock clock;

    WebhookNotificationProvider(HttpClient http, ObjectMapper json, NotificationDispatcherProperties properties,
            NotificationSecretResolver secrets) {
        this(http, json, properties, secrets, Clock.systemUTC());
    }

    WebhookNotificationProvider(HttpClient http, ObjectMapper json, NotificationDispatcherProperties properties,
            NotificationSecretResolver secrets, Clock clock) {
        this.http = http;
        this.json = json;
        this.properties = properties;
        this.secrets = secrets;
        this.clock = clock;
    }

    @Override
    public boolean supports(NotificationTask task) { return "WEBHOOK".equals(task.providerType()); }

    @Override
    public void deliver(NotificationTask task) {
        byte[] body;
        try {
            body = task.messagePayload() == null
                    ? json.writeValueAsBytes(new WebhookEnvelope(task.eventId(), task.id(), task.channelCode(),
                            task.eventType(), task.aggregateType(), task.aggregateId(), task.createdAt(), task.payload()))
                    : json.writeValueAsBytes(task.messagePayload());
        } catch (IOException exception) {
            throw new NotificationDeliveryException("WEBHOOK_SERIALIZATION_FAILURE", exception);
        }
        send(properties.validateEndpoint(task.endpointUri()), task, body);
    }

    private void send(URI endpoint, NotificationTask task, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(endpoint).timeout(properties.getRequestTimeout())
                .header("Content-Type", task.messageContentType() == null ? "application/json" : task.messageContentType())
                .header("User-Agent", "tpip-webhook-provider/0.1")
                .header("X-TPIP-Event-Id", Long.toString(task.eventId()))
                .header("X-TPIP-Delivery-Id", Long.toString(task.id()))
                .header("X-TPIP-Channel-Code", task.channelCode())
                .header("X-TPIP-Event-Type", task.eventType())
                .header("Idempotency-Key", "tpip-outbox-" + task.eventId() + "-channel-" + task.channelCode())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body));
        if (task.authorizationSecretRef() != null && !task.authorizationSecretRef().isBlank()) {
            builder.header("Authorization", "Bearer " + secrets.resolve(task.authorizationSecretRef()));
        }
        try {
            HttpResponse<Void> response = http.send(builder.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NotificationDeliveryException("WEBHOOK_HTTP_" + response.statusCode(),
                        RetryAfterParser.parse(response.headers(), clock));
            }
        } catch (IOException exception) {
            throw new NotificationDeliveryException("WEBHOOK_IO_FAILURE", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NotificationDeliveryException("WEBHOOK_INTERRUPTED", exception);
        }
    }

    private record WebhookEnvelope(long eventId, long deliveryId, String channelCode, String eventType,
            String aggregateType, String aggregateId, java.time.Instant occurredAt, JsonNode payload) {}
}
