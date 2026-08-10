package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record GlobalDriftPolicyImpactSnapshot(String snapshotId, long candidatePolicyId,
        long candidateVersionId, String candidateChecksum, String coverageChecksum, int workspaceCount,
        String impactChecksum, String impactDocument, String createdBy, Instant createdAt, Instant expiresAt,
        String publishUsedBy, Instant publishUsedAt, String activationUsedBy, Instant activationUsedAt) {
    public GlobalDriftPolicyImpactSnapshot {
        if (snapshotId == null || snapshotId.isBlank() || candidatePolicyId <= 0 || candidateVersionId <= 0
                || !checksum(candidateChecksum) || !checksum(coverageChecksum) || workspaceCount < 0
                || !checksum(impactChecksum) || impactDocument == null || impactDocument.isBlank()
                || createdBy == null || createdBy.isBlank() || createdAt == null || expiresAt == null
                || !expiresAt.isAfter(createdAt))
            throw new IllegalArgumentException("GlobalDriftPolicyImpactSnapshot is invalid");
        if ((publishUsedBy == null) != (publishUsedAt == null)
                || (activationUsedBy == null) != (activationUsedAt == null))
            throw new IllegalArgumentException("Global impact snapshot consumption evidence is inconsistent");
    }
    public boolean expired(Instant now) { return !expiresAt.isAfter(now); }
    private static boolean checksum(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }
}
