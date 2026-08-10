package com.ftk.tpip.access.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable selection and compilation result of a channel authentication template. */
public record ChannelAuthenticationVersion(Long id, long channelId, int versionNo,
        long authenticationTemplateVersionId, long credentialProfileId, String configurationDocument,
        String compiledPolicyDocument, String compilerVersion, String contentChecksum,
        AccessPolicyLifecycleStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");
    public ChannelAuthenticationVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (channelId <= 0 || authenticationTemplateVersionId <= 0 || credentialProfileId <= 0)
            throw new IllegalArgumentException("referenced ids must be positive");
        if (versionNo < 0) throw new IllegalArgumentException("versionNo must not be negative");
        configurationDocument = json(configurationDocument, "configurationDocument");
        compiledPolicyDocument = json(compiledPolicyDocument, "compiledPolicyDocument");
        compilerVersion = required(compilerVersion, "compilerVersion", 100);
        if (contentChecksum == null || !SHA.matcher(contentChecksum).matches())
            throw new IllegalArgumentException("invalid contentChecksum");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == AccessPolicyLifecycleStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("published channel authentication requires publishedAt");
    }
    private static String json(String value, String field) {
        return required(value, field, Integer.MAX_VALUE);
    }
    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
}
