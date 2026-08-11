package com.ftk.tpip.mcp.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record McpToolAsset(Long id, long operationId, String toolName, String displayName,
        String description, String ownerCode, Status status, long rowVersion,
        Instant createdAt, Instant updatedAt) {

    private static final Pattern TOOL_NAME = Pattern.compile("^[a-zA-Z0-9_-]{1,64}$");

    public McpToolAsset {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (operationId <= 0) throw new IllegalArgumentException("operationId must be positive");
        toolName = required(toolName, "toolName", 64);
        if (!TOOL_NAME.matcher(toolName).matches()) throw new IllegalArgumentException("toolName is invalid");
        displayName = required(displayName, "displayName", 200);
        description = required(description, "description", 1000);
        ownerCode = required(ownerCode, "ownerCode", 100);
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public static McpToolAsset create(long operationId, String toolName, String displayName,
            String description, String ownerCode) {
        return new McpToolAsset(null, operationId, toolName, displayName, description, ownerCode,
                Status.ACTIVE, 0, null, null);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " is blank or too long");
        }
        return normalized;
    }

    public enum Status { ACTIVE, INACTIVE }
}
