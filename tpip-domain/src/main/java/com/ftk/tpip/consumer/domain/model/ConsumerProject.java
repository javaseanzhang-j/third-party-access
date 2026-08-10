package com.ftk.tpip.consumer.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record ConsumerProject(Long id, AssetCode projectCode, String projectName, String ownerCode,
        String description, ConsumerStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public ConsumerProject {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        projectCode = Objects.requireNonNull(projectCode);
        projectName = required(projectName, "projectName", 200);
        ownerCode = required(ownerCode, "ownerCode", 100);
        description = optional(description, 1000);
        status = Objects.requireNonNull(status);
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }
    public static ConsumerProject create(AssetCode code, String name, String owner, String description) {
        return new ConsumerProject(null, code, name, owner, description, ConsumerStatus.ACTIVE, 0, null, null);
    }
    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim(); if (normalized.length() > max) throw new IllegalArgumentException("description is too long");
        return normalized;
    }
}
