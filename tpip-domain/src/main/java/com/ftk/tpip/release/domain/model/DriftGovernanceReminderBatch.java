package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record DriftGovernanceReminderBatch(Long id, String batchCode, long workspaceId, String aggregationKey,
        String environmentCode, String ownerCode, DriftGovernanceReminderBatchSource creationSource,
        DriftGovernanceReminderBatchStatus status, int memberCount, String payloadDocument, String contentChecksum,
        long rowVersion, Long outboxId, Long replacesBatchId, Long replacedByBatchId, String createdBy,
        Instant createdAt, String approvedBy, Instant approvedAt, String cancelReason, String cancelledBy,
        Instant cancelledAt, String dispatchedBy, Instant dispatchedAt) {
    public DriftGovernanceReminderBatch {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        batchCode = required(batchCode, "batchCode", 100);
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        sha256(aggregationKey, "aggregationKey");
        environmentCode = required(environmentCode, "environmentCode", 32);
        if (!environmentCode.matches("[a-z][a-z0-9_-]{0,31}"))
            throw new IllegalArgumentException("environmentCode is invalid");
        ownerCode = required(ownerCode, "ownerCode", 100);
        if (creationSource == null) throw new IllegalArgumentException("creationSource must not be null");
        if (status == null) throw new IllegalArgumentException("status must not be null");
        if (memberCount < 1 || memberCount > 100) throw new IllegalArgumentException("memberCount is invalid");
        if (payloadDocument == null || payloadDocument.isBlank() || payloadDocument.length() > 1_000_000)
            throw new IllegalArgumentException("payloadDocument is invalid");
        sha256(contentChecksum, "contentChecksum");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
        createdBy = required(createdBy, "createdBy", 100);
        if ((status == DriftGovernanceReminderBatchStatus.APPROVED
                || status == DriftGovernanceReminderBatchStatus.DISPATCHED)
                && (approvedBy == null || approvedBy.isBlank() || approvedAt == null))
            throw new IllegalArgumentException("Approved batch requires approval evidence");
        if (status == DriftGovernanceReminderBatchStatus.DISPATCHED
                && (outboxId == null || dispatchedBy == null || dispatchedAt == null))
            throw new IllegalArgumentException("DISPATCHED batch requires outbox evidence");
        if (status == DriftGovernanceReminderBatchStatus.CANCELLED) {
            cancelReason = required(cancelReason, "cancelReason", 500);
            cancelledBy = required(cancelledBy, "cancelledBy", 100);
            if (cancelledAt == null) throw new IllegalArgumentException("CANCELLED batch requires cancelledAt");
        } else if (cancelReason != null || cancelledBy != null || cancelledAt != null || replacedByBatchId != null) {
            throw new IllegalArgumentException("Only CANCELLED batch can contain cancellation evidence");
        }
        if (replacesBatchId != null && replacesBatchId <= 0 || replacedByBatchId != null && replacedByBatchId <= 0)
            throw new IllegalArgumentException("replacement references must be positive");
    }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException(field + " is invalid");
        return value.trim();
    }
    private static void sha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
    }
}
