package com.ftk.tpip.release.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record FixtureSuite(Long id, long bindingId, AssetCode suiteCode, String suiteName,
        String description, FixtureSuiteStatus status, long rowVersion, Instant createdAt, Instant updatedAt) {
    public FixtureSuite {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (bindingId <= 0) throw new IllegalArgumentException("bindingId must be positive");
        Objects.requireNonNull(suiteCode, "suiteCode must not be null");
        suiteName = required(suiteName, "suiteName", 200);
        description = optional(description, 1000);
        Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public static FixtureSuite create(long bindingId, AssetCode code, String name, String description) {
        return new FixtureSuite(null, bindingId, code, name, description, FixtureSuiteStatus.ACTIVE, 0, null, null);
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("description is too long");
        return normalized;
    }
}
