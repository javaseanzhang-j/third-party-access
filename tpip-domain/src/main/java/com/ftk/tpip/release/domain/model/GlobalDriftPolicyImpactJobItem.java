package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record GlobalDriftPolicyImpactJobItem(String jobId, long workspaceId, int itemOrder,
        Long currentPolicyId, Long currentVersionId, String currentChecksum,
        GlobalDriftPolicyImpactJobItemStatus status, int attemptCount, String leaseOwner, Instant leaseUntil,
        String workspaceSnapshotId, String failureCode, String failureMessage,
        Instant startedAt, Instant finishedAt) {
    public GlobalDriftPolicyImpactJobItem {
        if (jobId == null || jobId.isBlank() || workspaceId <= 0 || itemOrder < 0 || status == null
                || attemptCount < 0 || ((currentPolicyId == null || currentVersionId == null || currentChecksum == null)
                && !(currentPolicyId == null && currentVersionId == null && currentChecksum == null))
                || (currentChecksum != null && !currentChecksum.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("GlobalDriftPolicyImpactJobItem is invalid");
    }
}
