package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record DriftPolicyImpactSnapshot(String snapshotId, long workspaceId,
        long candidatePolicyId, long candidateVersionId, String candidateChecksum,
        Long currentPolicyId, Long currentVersionId, String currentChecksum,
        String impactChecksum, String impactDocument, String createdBy,
        Instant createdAt, Instant expiresAt, String publishUsedBy, Instant publishUsedAt,
        String activationUsedBy, Instant activationUsedAt) {
    public DriftPolicyImpactSnapshot {
        required(snapshotId, 36, "snapshotId");
        if (workspaceId <= 0 || candidatePolicyId <= 0 || candidateVersionId <= 0)
            throw new IllegalArgumentException("snapshot asset identities must be positive");
        checksum(candidateChecksum, "candidateChecksum");
        checksum(impactChecksum, "impactChecksum");
        boolean current = currentPolicyId != null || currentVersionId != null || currentChecksum != null;
        if (current && (currentPolicyId == null || currentPolicyId <= 0 || currentVersionId == null
                || currentVersionId <= 0 || currentChecksum == null))
            throw new IllegalArgumentException("current policy snapshot identity is incomplete");
        if (currentChecksum != null) checksum(currentChecksum, "currentChecksum");
        if (impactDocument == null || impactDocument.isBlank())
            throw new IllegalArgumentException("impactDocument must not be blank");
        required(createdBy, 100, "createdBy");
        if (createdAt == null || expiresAt == null || !createdAt.isBefore(expiresAt))
            throw new IllegalArgumentException("snapshot expiry is invalid");
        if ((publishUsedBy == null) != (publishUsedAt == null)
                || (activationUsedBy == null) != (activationUsedAt == null))
            throw new IllegalArgumentException("snapshot consumption evidence is incomplete");
    }
    public boolean expired(Instant now) { return !now.isBefore(expiresAt); }
    private static void checksum(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
    }
    private static void required(String value, int max, String field) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException(field + " is invalid");
    }
}
