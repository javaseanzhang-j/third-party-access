package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record GlobalDriftPolicyImpactJob(String jobId, long candidatePolicyId, long candidateVersionId,
        String candidateChecksum, String coverageChecksum, int workspaceCount, int succeededCount,
        int failedCount, GlobalDriftPolicyImpactJobStatus status, Instant snapshotAt, Instant expiresAt,
        String sealedSnapshotId, long rowVersion, String createdBy, Instant createdAt,
        String updatedBy, Instant updatedAt) {
    public GlobalDriftPolicyImpactJob {
        if (jobId == null || jobId.isBlank() || candidatePolicyId <= 0 || candidateVersionId <= 0
                || !checksum(candidateChecksum) || !checksum(coverageChecksum) || workspaceCount < 0
                || succeededCount < 0 || failedCount < 0 || succeededCount + failedCount > workspaceCount
                || status == null || snapshotAt == null || expiresAt == null || !expiresAt.isAfter(snapshotAt)
                || rowVersion < 0 || createdBy == null || createdBy.isBlank() || createdAt == null
                || updatedBy == null || updatedBy.isBlank() || updatedAt == null)
            throw new IllegalArgumentException("GlobalDriftPolicyImpactJob is invalid");
        if ((status == GlobalDriftPolicyImpactJobStatus.SEALED) != (sealedSnapshotId != null))
            throw new IllegalArgumentException("Global impact job sealed snapshot is inconsistent");
    }
    public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
    private static boolean checksum(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
