package com.ftk.tpip.mapping.api;

import java.util.Map;
import java.util.Objects;

public record MappingContext(
        String requestId,
        String traceId,
        String operationCode,
        Map<String, Object> attributes) {

    public MappingContext {
        requestId = Objects.requireNonNull(requestId, "requestId must not be null");
        traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        operationCode = Objects.requireNonNull(operationCode, "operationCode must not be null");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
