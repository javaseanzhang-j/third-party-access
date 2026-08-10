package com.ftk.tpip.access.domain.model;

import com.ftk.tpip.shared.SemanticVersion;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record AuthenticationTemplateVersion(Long id, long authenticationTemplateId, int versionNo,
        SemanticVersion semanticVersion, String credentialSchema, String configurationSchema,
        String templateDocument, String contentChecksum, AccessPolicyLifecycleStatus lifecycleStatus,
        Instant publishedAt, Instant createdAt) {
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");
    public AuthenticationTemplateVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (authenticationTemplateId <= 0) throw new IllegalArgumentException("authenticationTemplateId must be positive");
        if (versionNo < 0) throw new IllegalArgumentException("versionNo must not be negative");
        semanticVersion = Objects.requireNonNull(semanticVersion, "semanticVersion must not be null");
        credentialSchema = json(credentialSchema, "credentialSchema");
        configurationSchema = json(configurationSchema, "configurationSchema");
        templateDocument = json(templateDocument, "templateDocument");
        if (contentChecksum == null || !SHA.matcher(contentChecksum).matches())
            throw new IllegalArgumentException("invalid contentChecksum");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == AccessPolicyLifecycleStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("published template version requires publishedAt");
    }
    private static String json(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be empty");
        return normalized;
    }
}
