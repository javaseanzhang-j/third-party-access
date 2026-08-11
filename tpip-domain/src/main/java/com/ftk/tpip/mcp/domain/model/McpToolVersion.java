package com.ftk.tpip.mcp.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record McpToolVersion(Long id, long toolId, int versionNo, String title, String description,
        String fixedScenario, String inputSchema, String outputSchema, boolean readOnly,
        boolean destructive, boolean idempotent, boolean openWorld, ConfirmationMode confirmationMode,
        String contentChecksum, LifecycleStatus lifecycleStatus, String publishedBy,
        Instant publishedAt, String createdBy, Instant createdAt) {

    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern SCENARIO = Pattern.compile("^[a-zA-Z0-9._-]{1,100}$");

    public McpToolVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (toolId <= 0 || versionNo < 0) throw new IllegalArgumentException("tool/version is invalid");
        title = required(title, "title", 200);
        description = required(description, "description", 2000);
        fixedScenario = optionalScenario(fixedScenario);
        inputSchema = required(inputSchema, "inputSchema", 65535);
        outputSchema = outputSchema == null || outputSchema.isBlank() ? null : outputSchema.trim();
        confirmationMode = Objects.requireNonNull(confirmationMode, "confirmationMode must not be null");
        if (contentChecksum == null || !SHA256.matcher(contentChecksum).matches()) {
            throw new IllegalArgumentException("contentChecksum is invalid");
        }
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == LifecycleStatus.PUBLISHED
                && (publishedBy == null || publishedBy.isBlank() || publishedAt == null)) {
            throw new IllegalArgumentException("published version requires publisher and time");
        }
    }

    public static McpToolVersion draft(long toolId, String title, String description, String fixedScenario,
            String inputSchema, String outputSchema, boolean readOnly, boolean destructive,
            boolean idempotent, boolean openWorld, ConfirmationMode confirmationMode, String checksum) {
        return new McpToolVersion(null, toolId, 0, title, description, fixedScenario, inputSchema,
                outputSchema, readOnly, destructive, idempotent, openWorld, confirmationMode,
                checksum, LifecycleStatus.DRAFT, null, null, null, null);
    }

    private static String optionalScenario(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!SCENARIO.matcher(normalized).matches()) throw new IllegalArgumentException("fixedScenario is invalid");
        return normalized;
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " is blank or too long");
        }
        return normalized;
    }

    public enum ConfirmationMode { NONE, REQUIRED, ALWAYS }
    public enum LifecycleStatus { DRAFT, PUBLISHED }
}
