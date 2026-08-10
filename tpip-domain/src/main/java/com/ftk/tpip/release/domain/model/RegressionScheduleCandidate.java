package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.Objects;

public record RegressionScheduleCandidate(RegressionPolicy policy, RegressionPolicyVersion version,
        String environmentCode, Instant nextRunAt, int consecutiveFailures) {
    public RegressionScheduleCandidate {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(version, "version must not be null");
        if (environmentCode == null || !environmentCode.matches("[a-z0-9][a-z0-9_-]{0,63}"))
            throw new IllegalArgumentException("environmentCode is invalid");
        Objects.requireNonNull(nextRunAt, "nextRunAt must not be null");
        if (policy.status() != RegressionPolicyStatus.ACTIVE || !version.id().equals(policy.currentVersionId()))
            throw new IllegalArgumentException("schedule candidate must use the active policy version");
        if (consecutiveFailures < 0) throw new IllegalArgumentException("consecutiveFailures must not be negative");
    }
}
