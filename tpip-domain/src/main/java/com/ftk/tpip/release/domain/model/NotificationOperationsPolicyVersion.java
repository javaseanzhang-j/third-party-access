package com.ftk.tpip.release.domain.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

public record NotificationOperationsPolicyVersion(Long id, String environmentCode, int versionNo,
        int minimumOperationalAttempts, BigDecimal warningMinimumSuccessRate,
        BigDecimal criticalMinimumSuccessRate, Duration criticalEscalationAfter,
        Duration repeatNotificationAfter, String lifecycleStatus, String contentChecksum,
        String createdBy, String publishedBy, Instant publishedAt, Instant createdAt) {
    public NotificationOperationsPolicyVersion {
        if (id != null && id <= 0 || environmentCode == null || environmentCode.isBlank() || versionNo < 0
                || minimumOperationalAttempts < 1 || warningMinimumSuccessRate == null
                || criticalMinimumSuccessRate == null || criticalEscalationAfter == null
                || criticalEscalationAfter.isNegative() || repeatNotificationAfter == null
                || repeatNotificationAfter.isNegative() || lifecycleStatus == null || lifecycleStatus.isBlank()
                || contentChecksum == null || contentChecksum.length() != 64
                || createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("notification operations policy version is invalid");
        }
    }
}
