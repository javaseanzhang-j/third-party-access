package com.ftk.tpip.integration.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record IntegrationBinding(Long id, AssetCode bindingCode, String bindingName, long operationId,
        long providerContractId, String ownerCode, BindingStatus status, long rowVersion,
        Instant createdAt, Instant updatedAt) {
    public IntegrationBinding {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        bindingCode = Objects.requireNonNull(bindingCode, "bindingCode must not be null");
        bindingName = required(bindingName, "bindingName", 200);
        if (operationId <= 0) throw new IllegalArgumentException("operationId must be positive");
        if (providerContractId <= 0) throw new IllegalArgumentException("providerContractId must be positive");
        ownerCode = required(ownerCode, "ownerCode", 100);
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public static IntegrationBinding create(AssetCode code, String name, long operationId,
            long providerContractId, String ownerCode) {
        return new IntegrationBinding(null, code, name, operationId, providerContractId, ownerCode,
                BindingStatus.ACTIVE, 0, null, null);
    }

    public IntegrationBinding revise(String name, String ownerCode, BindingStatus status, long expectedVersion) {
        if (id == null) throw new IllegalStateException("unsaved binding cannot be revised");
        return new IntegrationBinding(id, bindingCode, name, operationId, providerContractId, ownerCode,
                status, expectedVersion, createdAt, updatedAt);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        return normalized;
    }
}
