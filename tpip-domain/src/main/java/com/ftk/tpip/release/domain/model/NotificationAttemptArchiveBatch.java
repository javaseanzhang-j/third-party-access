package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationAttemptArchiveBatch(Long id, String batchCode, String environmentCode,
        Instant windowStart, Instant windowEnd, long firstAttemptId, long lastAttemptId, long recordCount,
        String artifactUri, String artifactChecksum, Long artifactSizeBytes, String manifestDocument,
        String manifestChecksum, String status, boolean legalHold, String holdReason, String heldBy,
        Instant heldAt, String verifiedBy, Instant verifiedAt, String purgedBy, Instant purgedAt,
        Long purgedRecordCount, String failureReason, String createdBy, Instant createdAt, Instant updatedAt) {
    public NotificationAttemptArchiveBatch {
        if (id != null && id <= 0 || batchCode == null || batchCode.isBlank()
                || environmentCode == null || environmentCode.isBlank() || windowStart == null || windowEnd == null
                || !windowStart.isBefore(windowEnd) || firstAttemptId <= 0 || lastAttemptId < firstAttemptId
                || recordCount < 1 || status == null || status.isBlank() || createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("notification attempt archive batch is invalid");
        }
    }
}
