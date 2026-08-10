package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.Objects;

public record VerificationDriftBulkOperation(String commandKey, long workspaceId,
        VerificationDriftBulkOperationType operationType,
        boolean dryRun, String requestChecksum, VerificationDriftBulkOperationStatus status, String actorCode,
        int itemCount, int eligibleCount, int appliedCount, int rejectedCount,
        String requestDocument, String resultDocument, Instant createdAt) {
    public VerificationDriftBulkOperation {
        text(commandKey, 100, "commandKey");
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        Objects.requireNonNull(operationType, "operationType must not be null");
        if (requestChecksum == null || !requestChecksum.matches("^[a-f0-9]{64}$"))
            throw new IllegalArgumentException("requestChecksum is invalid");
        Objects.requireNonNull(status, "status must not be null");
        text(actorCode, 100, "actorCode");
        if (itemCount < 1 || itemCount > 100 || eligibleCount < 0 || appliedCount < 0 || rejectedCount < 0
                || eligibleCount + rejectedCount != itemCount || appliedCount > eligibleCount)
            throw new IllegalArgumentException("bulk operation counts are inconsistent");
        if (requestDocument == null || resultDocument == null)
            throw new IllegalArgumentException("bulk operation evidence must not be null");
        if (status == VerificationDriftBulkOperationStatus.PREVIEWED && (!dryRun || appliedCount != 0)
                || status == VerificationDriftBulkOperationStatus.REJECTED && (appliedCount != 0 || rejectedCount == 0)
                || status == VerificationDriftBulkOperationStatus.APPLIED
                    && (dryRun || rejectedCount != 0 || appliedCount != itemCount))
            throw new IllegalArgumentException("bulk operation status is inconsistent");
    }

    private static void text(String value, int maximum, String field) {
        if (value == null || value.isBlank() || value.trim().length() > maximum)
            throw new IllegalArgumentException(field + " is invalid");
    }
}
