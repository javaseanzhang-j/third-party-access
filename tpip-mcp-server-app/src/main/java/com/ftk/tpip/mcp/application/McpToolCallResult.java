package com.ftk.tpip.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;

public record McpToolCallResult(
        String requestId,
        String serviceCode,
        boolean success,
        String resultCode,
        String message,
        JsonNode structuredContent) {

    public McpToolCallResult {
        structuredContent = structuredContent == null ? null : structuredContent.deepCopy();
    }

    @Override
    public JsonNode structuredContent() {
        return structuredContent == null ? null : structuredContent.deepCopy();
    }
}
