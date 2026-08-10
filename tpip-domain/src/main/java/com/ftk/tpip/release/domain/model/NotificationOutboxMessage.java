package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationOutboxMessage(Long id, String eventType, String aggregateType,
        String aggregateId, String environmentCode, String payload, Instant availableAt, Instant createdAt) {
    public NotificationOutboxMessage {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        eventType = required(eventType, "eventType", 100);
        aggregateType = required(aggregateType, "aggregateType", 80);
        aggregateId = required(aggregateId, "aggregateId", 160);
        environmentCode = required(environmentCode, "environmentCode", 32);
        if (!environmentCode.matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("environmentCode is invalid");
        }
        payload = payload == null || payload.isBlank() ? "{}" : payload;
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
