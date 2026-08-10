package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationMaintenanceWindow(Long id, String environmentCode, Instant windowStart,
        Instant windowEnd, String reason, String status, String createdBy, String cancelledBy,
        Instant cancelledAt, Instant createdAt, Instant updatedAt) {
    public NotificationMaintenanceWindow {
        if (id != null && id <= 0 || environmentCode == null || environmentCode.isBlank()
                || windowStart == null || windowEnd == null || !windowStart.isBefore(windowEnd)
                || reason == null || reason.isBlank() || status == null || status.isBlank()
                || createdBy == null || createdBy.isBlank()) {
            throw new IllegalArgumentException("notification maintenance window is invalid");
        }
    }
}
