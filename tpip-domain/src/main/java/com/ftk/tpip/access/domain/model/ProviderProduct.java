package com.ftk.tpip.access.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record ProviderProduct(Long id, long providerId, AssetCode productCode, String productName,
        String description, AccessChannelStatus status, Instant createdAt) {
    public ProviderProduct {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (providerId <= 0) throw new IllegalArgumentException("providerId must be positive");
        productCode = Objects.requireNonNull(productCode);
        productName = required(productName, "productName", 200);
        description = optional(description, 1000);
        status = Objects.requireNonNull(status);
    }

    public static ProviderProduct create(long providerId, AssetCode code, String name, String description) {
        return new ProviderProduct(null, providerId, code, name, description, AccessChannelStatus.ACTIVE, null);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max)
            throw new IllegalArgumentException(field + " must contain 1 to " + max + " characters");
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("description is too long");
        return normalized;
    }
}
