package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationAttemptArchiveVerification(Long id, long archiveBatchId, String verificationType,
        String verificationResult, String artifactChecksum, String failureReason, String verifiedBy,
        Instant verifiedAt, long durationMillis) {
    public NotificationAttemptArchiveVerification {
        if (id != null && id <= 0 || archiveBatchId <= 0 || verificationType == null || verificationType.isBlank()
                || verificationResult == null || verificationResult.isBlank() || verifiedBy == null
                || verifiedBy.isBlank() || verifiedAt == null || durationMillis < 0) {
            throw new IllegalArgumentException("notification attempt archive verification is invalid");
        }
    }
}
