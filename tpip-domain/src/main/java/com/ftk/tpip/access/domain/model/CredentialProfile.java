package com.ftk.tpip.access.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

/** A named group of public credential identifiers and Secret references used by one or more channels. */
public record CredentialProfile(Long id, long providerId, AssetCode profileCode, String profileName,
        String credentialType, String description, AccessChannelStatus status, long rowVersion,
        Instant createdAt, Instant updatedAt) {
    public CredentialProfile {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (providerId <= 0) throw new IllegalArgumentException("providerId must be positive");
        profileCode = Objects.requireNonNull(profileCode, "profileCode must not be null");
        profileName = required(profileName, "profileName", 200);
        credentialType = required(credentialType, "credentialType", 64);
        description = optional(description, 1000);
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public static CredentialProfile create(long providerId, AssetCode code, String name, String type,
            String description) {
        return new CredentialProfile(null, providerId, code, name, type, description,
                AccessChannelStatus.ACTIVE, 0, null, null);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("description is too long");
        return normalized;
    }
}
