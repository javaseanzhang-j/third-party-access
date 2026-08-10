package com.ftk.tpip.release.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DriftGovernancePolicyVersion(Long id, long policyId, int versionNo, Duration overdueAfter,
        Duration aggregationWindow, Duration reminderInterval, int maximumReminders, String ownerCode,
        List<VerificationDriftKind> suppressedDriftKinds, List<String> suppressedCheckCodes,
        String contentChecksum, DriftGovernancePolicyVersionStatus lifecycleStatus,
        Instant publishedAt, Instant createdAt) {
    public DriftGovernancePolicyVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (policyId <= 0 || versionNo < 0) throw new IllegalArgumentException("invalid policy version identity");
        range(overdueAfter, Duration.ofHours(1), Duration.ofDays(365), "overdueAfter");
        range(aggregationWindow, Duration.ofHours(1), Duration.ofDays(30), "aggregationWindow");
        range(reminderInterval, Duration.ofHours(1), Duration.ofDays(30), "reminderInterval");
        if (maximumReminders < 1 || maximumReminders > 100)
            throw new IllegalArgumentException("maximumReminders must be between 1 and 100");
        if (ownerCode == null || ownerCode.isBlank() || ownerCode.trim().length() > 100)
            throw new IllegalArgumentException("ownerCode is invalid");
        ownerCode = ownerCode.trim();
        suppressedDriftKinds = List.copyOf(Objects.requireNonNull(suppressedDriftKinds));
        suppressedCheckCodes = List.copyOf(Objects.requireNonNull(suppressedCheckCodes));
        if (suppressedDriftKinds.size() > 5 || suppressedDriftKinds.stream().distinct().count() != suppressedDriftKinds.size())
            throw new IllegalArgumentException("suppressedDriftKinds are invalid or duplicated");
        if (suppressedCheckCodes.size() > 100 || suppressedCheckCodes.stream().anyMatch(value -> value == null
                || value.isBlank() || value.length() > 180)
                || suppressedCheckCodes.stream().distinct().count() != suppressedCheckCodes.size())
            throw new IllegalArgumentException("suppressedCheckCodes are invalid or duplicated");
        if (contentChecksum == null || !contentChecksum.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("contentChecksum must be lowercase SHA-256");
        Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == DriftGovernancePolicyVersionStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("PUBLISHED version requires publishedAt");
    }

    private static void range(Duration value, Duration minimum, Duration maximum, String field) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0)
            throw new IllegalArgumentException(field + " is outside the supported range");
    }
}
