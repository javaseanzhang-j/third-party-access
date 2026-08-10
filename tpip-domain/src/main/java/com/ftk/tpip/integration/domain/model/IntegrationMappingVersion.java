package com.ftk.tpip.integration.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

public record IntegrationMappingVersion(Long id, long mappingId, int versionNo,
        SelectorProfile selectorProfile, String sourceSchemaRef, String targetSchemaRef,
        String mappingOptions, String contentChecksum, MappingLifecycleStatus lifecycleStatus,
        Instant publishedAt, Instant createdAt, List<IntegrationMappingRule> rules) {
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");
    public IntegrationMappingVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (mappingId <= 0) throw new IllegalArgumentException("mappingId must be positive");
        if (versionNo < 0) throw new IllegalArgumentException("versionNo must not be negative");
        selectorProfile = Objects.requireNonNull(selectorProfile, "selectorProfile must not be null");
        if (contentChecksum == null || !SHA.matcher(contentChecksum).matches())
            throw new IllegalArgumentException("contentChecksum must be a lowercase SHA-256 value");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == MappingLifecycleStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("published version must have publishedAt");
        rules = List.copyOf(Objects.requireNonNull(rules, "rules must not be null"));
        if (rules.isEmpty()) throw new IllegalArgumentException("mapping version must contain at least one rule");
    }

    public static IntegrationMappingVersion draft(long mappingId, SelectorProfile profile,
            String sourceSchemaRef, String targetSchemaRef, String options, String checksum,
            List<IntegrationMappingRule> rules) {
        return new IntegrationMappingVersion(null, mappingId, 0, profile, sourceSchemaRef,
                targetSchemaRef, options, checksum, MappingLifecycleStatus.DRAFT, null, null, rules);
    }
}
