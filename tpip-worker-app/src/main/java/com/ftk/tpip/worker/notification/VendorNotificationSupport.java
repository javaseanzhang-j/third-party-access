package com.ftk.tpip.worker.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

final class VendorNotificationSupport {
    private final HttpClient http;
    private final ObjectMapper json;
    private final NotificationDispatcherProperties properties;
    private final Clock clock;

    VendorNotificationSupport(HttpClient http, ObjectMapper json, NotificationDispatcherProperties properties) {
        this(http, json, properties, Clock.systemUTC());
    }

    VendorNotificationSupport(HttpClient http, ObjectMapper json, NotificationDispatcherProperties properties,
            Clock clock) {
        this.http = http; this.json = json; this.properties = properties; this.clock = clock;
    }

    JsonNode configuration(NotificationTask task) {
        return task.providerConfiguration() == null || !task.providerConfiguration().isObject()
                ? json.createObjectNode() : task.providerConfiguration();
    }

    int rate(JsonNode configuration, int defaultValue) {
        int value = configuration.path("rateLimitPerSecond").asInt(defaultValue);
        if (value < 1 || value > 100) throw new NotificationDeliveryException("PROVIDER_CONFIGURATION_INVALID");
        return value;
    }

    URI endpoint(NotificationTask task, String query) {
        URI base = properties.validateEndpoint(task.endpointUri());
        try { return URI.create(base.toString() + "?" + query); }
        catch (IllegalArgumentException exception) {
            throw new NotificationDeliveryException(task.providerType() + "_ENDPOINT_INVALID", exception);
        }
    }

    JsonNode post(NotificationTask task, URI endpoint) {
        if (task.messagePayload() == null) throw new NotificationDeliveryException(task.providerType() + "_MESSAGE_MISSING");
        byte[] body;
        try { body = json.writeValueAsBytes(task.messagePayload()); }
        catch (JsonProcessingException exception) {
            throw new NotificationDeliveryException(task.providerType() + "_SERIALIZATION_FAILURE", exception);
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(properties.getRequestTimeout())
                .header("Content-Type", "application/json")
                .header("User-Agent", "tpip-" + task.providerType().toLowerCase() + "-provider/0.1")
                .header("X-TPIP-Delivery-Id", Long.toString(task.id()))
                .header("Idempotency-Key", "tpip-outbox-" + task.eventId() + "-channel-" + task.channelCode())
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new NotificationDeliveryException(task.providerType() + "_HTTP_" + response.statusCode(),
                        RetryAfterParser.parse(response.headers(), clock));
            }
            return json.readTree(response.body());
        } catch (JsonProcessingException exception) {
            throw new NotificationDeliveryException(task.providerType() + "_RESPONSE_INVALID", exception);
        } catch (IOException exception) {
            throw new NotificationDeliveryException(task.providerType() + "_IO_FAILURE", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NotificationDeliveryException(task.providerType() + "_INTERRUPTED", exception);
        }
    }

    static String query(String name, String value) {
        return name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
