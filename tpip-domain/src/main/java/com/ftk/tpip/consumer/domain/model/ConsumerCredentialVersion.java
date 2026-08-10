package com.ftk.tpip.consumer.domain.model;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record ConsumerCredentialVersion(Long id, long applicationId, int versionNo, String appKey,
        String secretReference, String algorithm, Instant validFrom, Instant validUntil,
        LifecycleStatus lifecycleStatus, String contentChecksum, Instant publishedAt, Instant createdAt) {
    private static final Pattern APP_KEY = Pattern.compile("^tpip_[a-z0-9]{24,64}$");
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");
    public ConsumerCredentialVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (applicationId <= 0 || versionNo < 0) throw new IllegalArgumentException("application/version is invalid");
        appKey = required(appKey, "appKey");
        if (!APP_KEY.matcher(appKey).matches()) throw new IllegalArgumentException("appKey is invalid");
        secretReference = required(secretReference, "secretReference");
        URI secret = URI.create(secretReference);
        if (!"env".equalsIgnoreCase(secret.getScheme())) throw new IllegalArgumentException("secretReference must use env://");
        algorithm = required(algorithm, "algorithm");
        if (!"HMAC_SHA256".equals(algorithm)) throw new IllegalArgumentException("only HMAC_SHA256 is supported");
        validFrom = Objects.requireNonNull(validFrom); lifecycleStatus = Objects.requireNonNull(lifecycleStatus);
        if (validUntil != null && !validUntil.isAfter(validFrom)) throw new IllegalArgumentException("validUntil must be after validFrom");
        if (contentChecksum == null || !SHA.matcher(contentChecksum).matches()) throw new IllegalArgumentException("invalid checksum");
        if (lifecycleStatus == LifecycleStatus.PUBLISHED && publishedAt == null) throw new IllegalArgumentException("published credential requires publishedAt");
    }
    public static ConsumerCredentialVersion draft(long applicationId, String appKey, String secretReference,
            Instant validFrom, Instant validUntil, String checksum) {
        return new ConsumerCredentialVersion(null, applicationId, 0, appKey, secretReference, "HMAC_SHA256",
                validFrom, validUntil, LifecycleStatus.DRAFT, checksum, null, null);
    }
    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is blank"); return normalized;
    }
    public enum LifecycleStatus { DRAFT, PUBLISHED, REVOKED }
}
