package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.Objects;

public record DeploymentHealthAlert(Long id, long deploymentId, long evaluationId, String alertCode,
        DeploymentHealthAlertSeverity severity, DeploymentHealthAlertStatus status, String summary,
        String details, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant createdAt,
        Instant updatedAt) {
    public DeploymentHealthAlert {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (deploymentId <= 0 || evaluationId <= 0) throw new IllegalArgumentException("alert references must be positive");
        alertCode = required(alertCode, "alertCode", 80);
        severity = Objects.requireNonNull(severity, "severity must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        summary = required(summary, "summary", 500);
        details = details == null || details.isBlank() ? "{}" : details;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
