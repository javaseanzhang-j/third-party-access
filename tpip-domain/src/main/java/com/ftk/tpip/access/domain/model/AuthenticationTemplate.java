package com.ftk.tpip.access.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

/** Stable identity of a form-driven authentication template. */
public record AuthenticationTemplate(Long id, Long providerId, AssetCode templateCode,
        String templateName, String templateType, String implementationRef, String description,
        AccessChannelStatus status, Instant createdAt) {
    public AuthenticationTemplate {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (providerId != null && providerId <= 0) throw new IllegalArgumentException("providerId must be positive");
        templateCode = Objects.requireNonNull(templateCode, "templateCode must not be null");
        templateName = required(templateName, "templateName", 200);
        templateType = required(templateType, "templateType", 64);
        implementationRef = required(implementationRef, "implementationRef", 500);
        description = optional(description, 1000);
        status = Objects.requireNonNull(status, "status must not be null");
    }

    public static AuthenticationTemplate create(Long providerId, AssetCode code, String name,
            String type, String implementationRef, String description) {
        return new AuthenticationTemplate(null, providerId, code, name, type, implementationRef,
                description, AccessChannelStatus.ACTIVE, null);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("description is too long");
        return normalized;
    }
}
