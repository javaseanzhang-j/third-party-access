package com.ftk.tpip.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.mcp.model.McpClientIdentity;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record McpToolCallCommand(
        McpClientIdentity identity,
        String toolName,
        String requestId,
        String idempotencyKey,
        String scenario,
        Instant deadline,
        JsonNode arguments,
        Map<String, String> attributes) {

    public McpToolCallCommand {
        identity = Objects.requireNonNull(identity, "identity must not be null");
        toolName = required(toolName, "toolName");
        requestId = required(requestId, "requestId");
        scenario = normalize(scenario);
        arguments = Objects.requireNonNull(arguments, "arguments must not be null").deepCopy();
        if (!arguments.isObject()) {
            throw new IllegalArgumentException("arguments must be a JSON object");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    @Override
    public JsonNode arguments() {
        return arguments.deepCopy();
    }

    private static String required(String value, String field) {
        String normalized = normalize(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
