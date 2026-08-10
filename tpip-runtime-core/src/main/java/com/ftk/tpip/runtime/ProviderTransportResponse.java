package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProviderTransportResponse(
        int statusCode,
        Map<String, List<String>> headers,
        JsonNode body,
        Duration duration) {
    public ProviderTransportResponse {
        if (statusCode < 100 || statusCode > 599) throw new IllegalArgumentException("Invalid HTTP status code");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = Objects.requireNonNull(body, "body must not be null");
        duration = Objects.requireNonNull(duration, "duration must not be null");
    }

    public boolean successful() { return statusCode >= 200 && statusCode < 300; }
}
