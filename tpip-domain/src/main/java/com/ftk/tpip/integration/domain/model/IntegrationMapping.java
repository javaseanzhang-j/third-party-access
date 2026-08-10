package com.ftk.tpip.integration.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record IntegrationMapping(Long id, long bindingId, AssetCode mappingCode, String mappingName,
        MappingAssetDirection direction, MappingStatus status, long rowVersion,
        Instant createdAt, Instant updatedAt) {
    public IntegrationMapping {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (bindingId <= 0) throw new IllegalArgumentException("bindingId must be positive");
        mappingCode = Objects.requireNonNull(mappingCode, "mappingCode must not be null");
        mappingName = required(mappingName, "mappingName", 200);
        direction = Objects.requireNonNull(direction, "direction must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public static IntegrationMapping create(long bindingId, AssetCode code, String name,
            MappingAssetDirection direction) {
        return new IntegrationMapping(null, bindingId, code, name, direction, MappingStatus.ACTIVE,
                0, null, null);
    }

    public IntegrationMapping revise(String name, MappingStatus revisedStatus, long expectedVersion) {
        if (id == null) throw new IllegalStateException("unsaved mapping cannot be revised");
        return new IntegrationMapping(id, bindingId, mappingCode, name, direction, revisedStatus,
                expectedVersion, createdAt, updatedAt);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        return normalized;
    }
}
