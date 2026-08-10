package com.ftk.tpip.provider.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record Provider(
        Long id,
        AssetCode providerCode,
        String providerName,
        ProviderType providerType,
        String description,
        String ownerCode,
        ProviderStatus status,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    public Provider {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        providerCode = Objects.requireNonNull(providerCode, "providerCode must not be null");
        providerName = requiredText(providerName, "providerName", 200);
        providerType = Objects.requireNonNull(providerType, "providerType must not be null");
        description = optionalText(description, "description", 1000);
        ownerCode = requiredText(ownerCode, "ownerCode", 100);
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
    }

    public static Provider create(
            AssetCode providerCode,
            String providerName,
            ProviderType providerType,
            String description,
            String ownerCode) {
        return new Provider(
                null,
                providerCode,
                providerName,
                providerType,
                description,
                ownerCode,
                ProviderStatus.ACTIVE,
                0,
                null,
                null);
    }

    public Provider revise(
            String providerName,
            ProviderType providerType,
            String description,
            String ownerCode,
            ProviderStatus status,
            long expectedVersion) {
        if (id == null) {
            throw new IllegalStateException("unsaved provider cannot be revised");
        }
        return new Provider(
                id,
                providerCode,
                providerName,
                providerType,
                description,
                ownerCode,
                status,
                expectedVersion,
                createdAt,
                updatedAt);
    }

    private static String requiredText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
