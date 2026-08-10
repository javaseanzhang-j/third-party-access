package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ProviderTransportRequest(
        URI uri,
        String method,
        Map<String, List<String>> headers,
        JsonNode body,
        Duration connectTimeout,
        Duration readTimeout,
        Duration totalTimeout) {
    public ProviderTransportRequest {
        uri = Objects.requireNonNull(uri, "uri must not be null");
        method = Objects.requireNonNull(method, "method must not be null");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = Objects.requireNonNull(body, "body must not be null");
        connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout must not be null");
        readTimeout = Objects.requireNonNull(readTimeout, "readTimeout must not be null");
        totalTimeout = Objects.requireNonNull(totalTimeout, "totalTimeout must not be null");
    }
}
