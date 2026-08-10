package com.ftk.tpip.release.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record NotificationOperationsEvaluation(Long id, String environmentCode, Instant windowStart,
        Instant windowEnd, long attemptCount, long successCount, long failureCount, long deadLetterCount,
        BigDecimal successRate, String healthStatus, String thresholdSnapshot, String evaluatedBy,
        Instant evaluatedAt) {
    public NotificationOperationsEvaluation {
        if (id != null && id <= 0 || environmentCode == null || environmentCode.isBlank()
                || windowStart == null || windowEnd == null || !windowStart.isBefore(windowEnd)
                || attemptCount < 0 || successCount < 0 || failureCount < 0 || deadLetterCount < 0
                || successCount + failureCount != attemptCount || deadLetterCount > failureCount
                || successRate == null || healthStatus == null || healthStatus.isBlank()
                || thresholdSnapshot == null || thresholdSnapshot.isBlank()
                || evaluatedBy == null || evaluatedBy.isBlank() || evaluatedAt == null) {
            throw new IllegalArgumentException("notification operations evaluation is invalid");
        }
    }
}
