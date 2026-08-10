package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record RegressionScheduleState(long policyId, long policyVersionId, Instant nextRunAt,
        String leaseOwner, Instant leaseUntil, int consecutiveFailures, Instant lastRunAt,
        Long lastVerificationRunId, Long lastDriftReportId, String lastOutcome, String lastError, Instant updatedAt) {
    public RegressionScheduleState {
        if (policyId <= 0 || policyVersionId <= 0) throw new IllegalArgumentException("schedule references must be positive");
        if (consecutiveFailures < 0) throw new IllegalArgumentException("consecutiveFailures must not be negative");
        if ((leaseOwner == null) != (leaseUntil == null)) throw new IllegalArgumentException("lease fields must be set together");
        if (lastVerificationRunId != null && lastVerificationRunId <= 0)
            throw new IllegalArgumentException("lastVerificationRunId must be positive");
        if (lastDriftReportId != null && lastDriftReportId <= 0)
            throw new IllegalArgumentException("lastDriftReportId must be positive");
    }
}
