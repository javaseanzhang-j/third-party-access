package com.ftk.tpip.mcp.model;

public record McpClientIdentity(long applicationId, String applicationCode, String tenantId) {

    public McpClientIdentity {
        if (applicationId <= 0) {
            throw new IllegalArgumentException("applicationId must be positive");
        }
        applicationCode = required(applicationCode, "applicationCode");
        tenantId = normalize(tenantId);
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
