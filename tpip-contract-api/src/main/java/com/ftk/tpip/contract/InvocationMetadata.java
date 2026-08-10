package com.ftk.tpip.contract;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record InvocationMetadata(
        String requestId,
        String caller,
        String tenantId,
        String idempotencyKey,
        Instant deadline,
        Map<String, String> attributes) {

    public InvocationMetadata {
        requestId = Objects.requireNonNull(requestId, "requestId must not be null");
        caller = Objects.requireNonNull(caller, "caller must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
