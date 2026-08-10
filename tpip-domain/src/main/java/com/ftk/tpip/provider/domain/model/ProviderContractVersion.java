package com.ftk.tpip.provider.domain.model;

import com.ftk.tpip.shared.SemanticVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record ProviderContractVersion(
        Long id,
        long providerContractId,
        int versionNo,
        SemanticVersion semanticVersion,
        String requestSchema,
        String responseSchema,
        String errorSchema,
        String callbackSchema,
        String examples,
        String contentChecksum,
        ContractLifecycleStatus lifecycleStatus,
        Instant publishedAt,
        Instant createdAt) {

    private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");

    public ProviderContractVersion {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (providerContractId <= 0) {
            throw new IllegalArgumentException("providerContractId must be positive");
        }
        if (versionNo < 0) {
            throw new IllegalArgumentException("versionNo must not be negative");
        }
        semanticVersion = Objects.requireNonNull(semanticVersion, "semanticVersion must not be null");
        if (requestSchema == null && responseSchema == null && callbackSchema == null) {
            throw new IllegalArgumentException(
                    "at least one of requestSchema, responseSchema or callbackSchema is required");
        }
        contentChecksum = Objects.requireNonNull(contentChecksum, "contentChecksum must not be null");
        if (!SHA_256.matcher(contentChecksum).matches()) {
            throw new IllegalArgumentException("contentChecksum must be a lowercase SHA-256 value");
        }
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == ContractLifecycleStatus.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException("published version must have publishedAt");
        }
    }

    public static ProviderContractVersion draft(
            long providerContractId,
            SemanticVersion semanticVersion,
            String requestSchema,
            String responseSchema,
            String errorSchema,
            String callbackSchema,
            String examples,
            String contentChecksum) {
        return new ProviderContractVersion(
                null,
                providerContractId,
                0,
                semanticVersion,
                requestSchema,
                responseSchema,
                errorSchema,
                callbackSchema,
                examples,
                contentChecksum,
                ContractLifecycleStatus.DRAFT,
                null,
                null);
    }
}
