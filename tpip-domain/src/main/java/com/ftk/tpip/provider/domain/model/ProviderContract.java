package com.ftk.tpip.provider.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record ProviderContract(
        Long id,
        long providerId,
        AssetCode contractCode,
        String contractName,
        ProtocolType protocolType,
        String description,
        ContractStatus status,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    public ProviderContract {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (providerId <= 0) {
            throw new IllegalArgumentException("providerId must be positive");
        }
        contractCode = Objects.requireNonNull(contractCode, "contractCode must not be null");
        contractName = requiredText(contractName, "contractName", 200);
        protocolType = Objects.requireNonNull(protocolType, "protocolType must not be null");
        description = optionalText(description, "description", 1000);
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) {
            throw new IllegalArgumentException("rowVersion must not be negative");
        }
    }

    public static ProviderContract create(
            long providerId,
            AssetCode contractCode,
            String contractName,
            ProtocolType protocolType,
            String description) {
        return new ProviderContract(
                null,
                providerId,
                contractCode,
                contractName,
                protocolType,
                description,
                ContractStatus.ACTIVE,
                0,
                null,
                null);
    }

    public ProviderContract revise(
            String contractName,
            ProtocolType protocolType,
            String description,
            ContractStatus status,
            long expectedVersion) {
        if (id == null) {
            throw new IllegalStateException("unsaved contract cannot be revised");
        }
        return new ProviderContract(
                id,
                providerId,
                contractCode,
                contractName,
                protocolType,
                description,
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
