package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record GlobalDriftPolicyImpactJobPurgeReceipt(String receiptId, String jobId,
        long candidatePolicyId, long candidateVersionId, String candidateChecksum, String coverageChecksum,
        GlobalDriftPolicyImpactJobStatus terminalStatus, int workspaceCount, int succeededCount,
        int failedCount, int itemCount, int orphanSnapshotCount, int deletedSnapshotCount,
        String reason, String purgedBy, Instant purgedAt) {
    public GlobalDriftPolicyImpactJobPurgeReceipt {
        if (receiptId == null || receiptId.isBlank() || jobId == null || jobId.isBlank()
                || candidatePolicyId <= 0 || candidateVersionId <= 0 || candidateChecksum == null
                || coverageChecksum == null || (terminalStatus != GlobalDriftPolicyImpactJobStatus.CANCELLED
                && terminalStatus != GlobalDriftPolicyImpactJobStatus.EXPIRED) || workspaceCount < 0
                || succeededCount < 0 || failedCount < 0 || itemCount < 0 || orphanSnapshotCount < 0
                || deletedSnapshotCount < 0 || deletedSnapshotCount > orphanSnapshotCount
                || reason == null || reason.isBlank() || purgedBy == null || purgedBy.isBlank() || purgedAt == null) {
            throw new IllegalArgumentException("Global impact job purge receipt is invalid");
        }
    }
}
