package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record VerificationBaseline(Long id, long workspaceId, long fixtureSuiteVersionId,
        long sourceVerificationRunId, String baselineChecksum, String snapshotDocument,
        Long predecessorBaselineId, Long acceptedDriftReportId, Instant createdAt) {
    public VerificationBaseline(Long id, long workspaceId, long fixtureSuiteVersionId,
            long sourceVerificationRunId, String baselineChecksum, String snapshotDocument, Instant createdAt) {
        this(id, workspaceId, fixtureSuiteVersionId, sourceVerificationRunId, baselineChecksum, snapshotDocument,
                null, null, createdAt);
    }

    public VerificationBaseline {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (workspaceId <= 0 || fixtureSuiteVersionId <= 0 || sourceVerificationRunId <= 0)
            throw new IllegalArgumentException("baseline references must be positive");
        baselineChecksum = requiredChecksum(baselineChecksum);
        if (snapshotDocument == null || snapshotDocument.isBlank())
            throw new IllegalArgumentException("snapshotDocument must not be blank");
        if ((predecessorBaselineId == null) != (acceptedDriftReportId == null))
            throw new IllegalArgumentException("baseline lineage fields must be set together");
        if (predecessorBaselineId != null && (predecessorBaselineId <= 0 || acceptedDriftReportId <= 0))
            throw new IllegalArgumentException("baseline lineage references must be positive");
    }

    private static String requiredChecksum(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("baselineChecksum must be lowercase SHA-256");
        return value;
    }
}
