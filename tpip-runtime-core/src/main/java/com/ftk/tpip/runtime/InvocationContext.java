package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class InvocationContext {

    private final String requestId;
    private final String traceId;
    private final String operationCode;
    private final Instant startedAt;
    private final Map<String, Object> attributes = new HashMap<>();
    private final Map<String, List<String>> transportHeaders = new LinkedHashMap<>();
    private final Set<String> sensitiveTransportHeaders = new HashSet<>();
    private final Set<String> sensitiveAttributes = new HashSet<>();
    private final List<RuntimeStageObservation> stageObservations = new ArrayList<>();
    private JsonNode canonicalRequest;
    private JsonNode providerRequest;
    private JsonNode providerResponse;
    private JsonNode canonicalResponse;
    private Integer providerStatusCode;

    public InvocationContext(String requestId, String traceId, String operationCode, JsonNode canonicalRequest) {
        this.requestId = Objects.requireNonNull(requestId, "requestId must not be null");
        this.traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        this.operationCode = Objects.requireNonNull(operationCode, "operationCode must not be null");
        this.canonicalRequest = Objects.requireNonNull(canonicalRequest, "canonicalRequest must not be null");
        this.startedAt = Instant.now();
    }

    public String requestId() { return requestId; }
    public String traceId() { return traceId; }
    public String operationCode() { return operationCode; }
    public Instant startedAt() { return startedAt; }
    public JsonNode canonicalRequest() { return canonicalRequest; }
    public JsonNode providerRequest() { return providerRequest; }
    public JsonNode providerResponse() { return providerResponse; }
    public JsonNode canonicalResponse() { return canonicalResponse; }
    public Integer providerStatusCode() { return providerStatusCode; }
    public Map<String, Object> attributes() { return Map.copyOf(attributes); }
    public Map<String, List<String>> transportHeaders() { return Map.copyOf(transportHeaders); }
    public List<RuntimeStageObservation> stageObservations() { return List.copyOf(stageObservations); }

    public void providerRequest(JsonNode value) { providerRequest = value; }
    public void providerResponse(JsonNode value) { providerResponse = value; }
    public void canonicalResponse(JsonNode value) { canonicalResponse = value; }
    public void providerStatusCode(int value) { providerStatusCode = value; }
    public void putAttribute(String name, Object value) { attributes.put(name, value); }
    public void putSensitiveAttribute(String name,Object value){putAttribute(name,value);sensitiveAttributes.add(name);}
    public void putTransportHeader(String name, String value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("header name must not be blank");
        transportHeaders.put(name.trim(), List.of(Objects.requireNonNull(value, "header value must not be null")));
    }
    public void putSensitiveTransportHeader(String name, String value) {
        putTransportHeader(name, value);
        sensitiveTransportHeaders.add(name.trim());
    }
    public void clearSensitiveTransportHeaders() {
        sensitiveTransportHeaders.forEach(transportHeaders::remove);
        sensitiveTransportHeaders.clear();
    }
    public void clearSensitiveValues(){clearSensitiveTransportHeaders();sensitiveAttributes.forEach(attributes::remove);sensitiveAttributes.clear();}
    public void recordStage(RuntimeStage stage, long durationNanos, boolean success) {
        stageObservations.add(new RuntimeStageObservation(stage,
                java.time.Duration.ofNanos(Math.max(0, durationNanos)), success));
    }
}
