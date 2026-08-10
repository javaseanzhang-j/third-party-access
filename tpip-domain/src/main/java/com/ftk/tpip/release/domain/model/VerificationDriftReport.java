package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.Objects;

public record VerificationDriftReport(Long id, long baselineId, long verificationRunId,
        VerificationDriftStatus driftStatus, int comparedCheckCount, int driftCount,
        String reportDocument, Instant createdAt) {
    public VerificationDriftReport {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (baselineId <= 0 || verificationRunId <= 0)
            throw new IllegalArgumentException("drift report references must be positive");
        Objects.requireNonNull(driftStatus, "driftStatus must not be null");
        if (comparedCheckCount < 0 || driftCount < 0)
            throw new IllegalArgumentException("drift report counts must not be negative");
        if (driftStatus == VerificationDriftStatus.NO_DRIFT && driftCount != 0)
            throw new IllegalArgumentException("NO_DRIFT report cannot contain drift items");
        if (driftStatus == VerificationDriftStatus.DRIFTED && driftCount == 0)
            throw new IllegalArgumentException("DRIFTED report requires drift items");
        if (reportDocument == null || reportDocument.isBlank())
            throw new IllegalArgumentException("reportDocument must not be blank");
    }
}
