package com.ftk.tpip.consumer.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record ConsumerApplication(Long id, long projectId, AssetCode appCode, String appName,
        String ownerCode, String description, ConsumerStatus status, long rowVersion,
        Instant createdAt, Instant updatedAt) {
    public ConsumerApplication {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (projectId <= 0) throw new IllegalArgumentException("projectId must be positive");
        appCode = Objects.requireNonNull(appCode);
        appName = required(appName, "appName", 200); ownerCode = required(ownerCode, "ownerCode", 100);
        description = description == null || description.isBlank() ? null : description.trim();
        if (description != null && description.length() > 1000) throw new IllegalArgumentException("description is too long");
        status = Objects.requireNonNull(status);
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }
    public static ConsumerApplication create(long projectId, AssetCode code, String name, String owner, String description) {
        return new ConsumerApplication(null, projectId, code, name, owner, description, ConsumerStatus.ACTIVE, 0, null, null);
    }
    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
}
