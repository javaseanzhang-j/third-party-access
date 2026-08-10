package com.ftk.tpip.access.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record CredentialProfileItem(Long id, long credentialProfileId, AssetCode fieldCode,
        String fieldName, CredentialValueSource valueSource, String publicValue, Long secretRefId,
        boolean sensitive, String description, Instant createdAt, Instant updatedAt) {
    public CredentialProfileItem {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (credentialProfileId <= 0) throw new IllegalArgumentException("credentialProfileId must be positive");
        fieldCode = Objects.requireNonNull(fieldCode, "fieldCode must not be null");
        fieldName = required(fieldName, "fieldName", 200);
        valueSource = Objects.requireNonNull(valueSource, "valueSource must not be null");
        publicValue = optional(publicValue, 1000);
        if (secretRefId != null && secretRefId <= 0) throw new IllegalArgumentException("secretRefId must be positive");
        if (valueSource == CredentialValueSource.PUBLIC_VALUE && (publicValue == null || secretRefId != null))
            throw new IllegalArgumentException("PUBLIC_VALUE requires publicValue only");
        if (valueSource == CredentialValueSource.SECRET_REF && (publicValue != null || secretRefId == null))
            throw new IllegalArgumentException("SECRET_REF requires secretRefId only");
        description = optional(description, 1000);
    }

    public static CredentialProfileItem publicValue(long profileId, AssetCode code, String name,
            String value, boolean sensitive, String description) {
        return new CredentialProfileItem(null, profileId, code, name, CredentialValueSource.PUBLIC_VALUE,
                value, null, sensitive, description, null, null);
    }

    public static CredentialProfileItem secretRef(long profileId, AssetCode code, String name,
            long secretRefId, String description) {
        return new CredentialProfileItem(null, profileId, code, name, CredentialValueSource.SECRET_REF,
                null, secretRefId, true, description, null, null);
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("value is too long");
        return normalized;
    }
}
