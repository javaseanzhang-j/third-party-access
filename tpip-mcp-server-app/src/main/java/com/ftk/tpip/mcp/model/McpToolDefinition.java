package com.ftk.tpip.mcp.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.regex.Pattern;

public record McpToolDefinition(
        long toolId,
        String toolName,
        String title,
        String description,
        String serviceCode,
        int versionNo,
        JsonNode inputSchema,
        JsonNode outputSchema,
        McpToolAnnotations annotations,
        String contentChecksum) {

    private static final Pattern TOOL_NAME = Pattern.compile("^[a-z][a-z0-9_-]{0,127}$");
    private static final Pattern SERVICE_CODE = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");

    public McpToolDefinition {
        if (toolId <= 0) {
            throw new IllegalArgumentException("toolId must be positive");
        }
        toolName = required(toolName, "toolName");
        if (!TOOL_NAME.matcher(toolName).matches()) {
            throw new IllegalArgumentException("toolName must be a stable lower-case MCP identifier");
        }
        title = required(title, "title");
        description = required(description, "description");
        serviceCode = required(serviceCode, "serviceCode");
        if (!SERVICE_CODE.matcher(serviceCode).matches()) {
            throw new IllegalArgumentException("serviceCode is invalid");
        }
        if (versionNo <= 0) {
            throw new IllegalArgumentException("versionNo must be positive");
        }
        inputSchema = objectSchema(inputSchema, "inputSchema");
        outputSchema = outputSchema == null ? null : objectSchema(outputSchema, "outputSchema");
        annotations = Objects.requireNonNull(annotations, "annotations must not be null");
        contentChecksum = required(contentChecksum, "contentChecksum");
    }

    @Override
    public JsonNode inputSchema() {
        return inputSchema.deepCopy();
    }

    @Override
    public JsonNode outputSchema() {
        return outputSchema == null ? null : outputSchema.deepCopy();
    }

    private static JsonNode objectSchema(JsonNode schema, String field) {
        Objects.requireNonNull(schema, field + " must not be null");
        if (!schema.isObject() || !"object".equals(schema.path("type").asText())) {
            throw new IllegalArgumentException(field + " root type must be object");
        }
        return schema.deepCopy();
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
