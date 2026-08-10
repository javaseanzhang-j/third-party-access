package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationOperationsAlert(Long id, String environmentCode, long evaluationId,
        String alertCode, String severity, String status, String summary, String details,
        String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt, Instant escalatedAt,
        Instant lastNotifiedAt, Instant createdAt, Instant updatedAt) {
    public NotificationOperationsAlert {
        if (id != null && id <= 0 || environmentCode == null || environmentCode.isBlank()
                || evaluationId <= 0 || alertCode == null || alertCode.isBlank()
                || severity == null || severity.isBlank() || status == null || status.isBlank()
                || summary == null || summary.isBlank() || details == null || details.isBlank()) {
            throw new IllegalArgumentException("notification operations alert is invalid");
        }
    }
}
