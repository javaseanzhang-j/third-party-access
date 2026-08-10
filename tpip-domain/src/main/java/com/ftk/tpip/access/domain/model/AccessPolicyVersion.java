package com.ftk.tpip.access.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Immutable version of a channel-wide or interface-specific Policy DSL layer. */
public record AccessPolicyVersion(Long id, long channelId, AccessParameterScope scope, Long providerContractId,
        AssetCode policyCode, String policyName, int versionNo, String normalizedDocument,
        Set<String> disabledStepIds, String compilerVersion, String contentChecksum,
        AccessPolicyLifecycleStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern STEP = Pattern.compile("^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$");

    public AccessPolicyVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (channelId <= 0) throw new IllegalArgumentException("channelId must be positive");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        if (scope == AccessParameterScope.CHANNEL && providerContractId != null)
            throw new IllegalArgumentException("CHANNEL policy must not reference providerContractId");
        if (scope == AccessParameterScope.INTERFACE && (providerContractId == null || providerContractId <= 0))
            throw new IllegalArgumentException("INTERFACE policy requires providerContractId");
        Objects.requireNonNull(policyCode, "policyCode must not be null");
        policyName = required(policyName, "policyName", 200);
        if (versionNo < 0) throw new IllegalArgumentException("versionNo must not be negative");
        normalizedDocument = optional(normalizedDocument);
        disabledStepIds = disabledStepIds == null ? Set.of() : Collections.unmodifiableSet(new TreeSet<>(disabledStepIds));
        disabledStepIds.forEach(value -> { if (!STEP.matcher(value).matches()) throw new IllegalArgumentException("invalid disabled step id: " + value); });
        if (normalizedDocument == null && disabledStepIds.isEmpty())
            throw new IllegalArgumentException("policy version requires a document or disabled step ids");
        compilerVersion = required(compilerVersion, "compilerVersion", 100);
        if (contentChecksum == null || !SHA.matcher(contentChecksum).matches())
            throw new IllegalArgumentException("invalid contentChecksum");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == AccessPolicyLifecycleStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("published policy version requires publishedAt");
    }

    public static AccessPolicyVersion draft(long channelId, AccessParameterScope scope, Long providerContractId,
            AssetCode code, String name, String document, Set<String> disabled, String compiler, String checksum) {
        return new AccessPolicyVersion(null, channelId, scope, providerContractId, code, name, 0, document,
                disabled, compiler, checksum, AccessPolicyLifecycleStatus.DRAFT, null, null);
    }

    public String scopeKey() { return scope == AccessParameterScope.CHANNEL ? "CHANNEL" : "INTERFACE:" + providerContractId; }
    private static String required(String value, String field, int max) {
        String result = Objects.requireNonNull(value, field + " must not be null").trim();
        if (result.isEmpty() || result.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return result;
    }
    private static String optional(String value) { return value == null || value.isBlank() ? null : value; }
}
