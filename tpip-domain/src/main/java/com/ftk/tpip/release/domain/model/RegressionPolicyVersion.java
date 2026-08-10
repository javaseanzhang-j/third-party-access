package com.ftk.tpip.release.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record RegressionPolicyVersion(Long id, long policyId, long baselineId, int versionNo, Duration interval,
        Duration failureBackoff, int maximumConsecutiveFailures,
        RegressionPolicyVersionStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {
    public RegressionPolicyVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (policyId <= 0 || baselineId <= 0 || versionNo < 0)
            throw new IllegalArgumentException("invalid policy version identity");
        requireRange(interval, Duration.ofMinutes(1), Duration.ofDays(30), "interval");
        requireRange(failureBackoff, Duration.ofMinutes(1), Duration.ofDays(1), "failureBackoff");
        if (maximumConsecutiveFailures < 1 || maximumConsecutiveFailures > 100)
            throw new IllegalArgumentException("maximumConsecutiveFailures must be between 1 and 100");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == RegressionPolicyVersionStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("PUBLISHED policy version requires publishedAt");
    }

    private static void requireRange(Duration value, Duration minimum, Duration maximum, String field) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0)
            throw new IllegalArgumentException(field + " is outside the supported range");
    }
}
