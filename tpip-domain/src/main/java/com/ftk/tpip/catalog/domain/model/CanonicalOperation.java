package com.ftk.tpip.catalog.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record CanonicalOperation(
        Long id,
        long capabilityId,
        AssetCode operationCode,
        String operationName,
        String description,
        InvocationMode invocationMode,
        IdempotencyClass idempotencyClass,
        DataClassification dataClassification,
        String ownerCode,
        OperationStatus status,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    public CanonicalOperation {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (capabilityId <= 0) throw new IllegalArgumentException("capabilityId must be positive");
        operationCode = Objects.requireNonNull(operationCode, "operationCode must not be null");
        operationName = required(operationName, "operationName", 200);
        description = optional(description, "description", 1000);
        invocationMode = Objects.requireNonNull(invocationMode, "invocationMode must not be null");
        idempotencyClass = Objects.requireNonNull(idempotencyClass, "idempotencyClass must not be null");
        dataClassification = Objects.requireNonNull(dataClassification, "dataClassification must not be null");
        ownerCode = required(ownerCode, "ownerCode", 100);
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public static CanonicalOperation create(long capabilityId, AssetCode code, String name, String description,
            InvocationMode mode, IdempotencyClass idempotency, DataClassification classification, String ownerCode) {
        return new CanonicalOperation(null, capabilityId, code, name, description, mode, idempotency,
                classification, ownerCode, OperationStatus.ACTIVE, 0, null, null);
    }

    public CanonicalOperation revise(String name, String description, InvocationMode mode,
            IdempotencyClass idempotency, DataClassification classification, String ownerCode,
            OperationStatus status, long expectedVersion) {
        if (id == null) throw new IllegalStateException("unsaved operation cannot be revised");
        return new CanonicalOperation(id, capabilityId, operationCode, name, description, mode, idempotency,
                classification, ownerCode, status, expectedVersion, createdAt, updatedAt);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        return normalized;
    }

    private static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        return normalized;
    }
}
