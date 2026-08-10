package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationDeliveryAttemptEvidence(long id, long deliveryId, String environmentCode,
        int attemptNo, String providerType, String channelCode, Long endpointRevisionId, String outcome,
        String errorCode, String failureClass, Long retryDelayMillis, boolean terminalFailure, Instant occurredAt) {
    public NotificationDeliveryAttemptEvidence {
        if (id <= 0 || deliveryId <= 0 || environmentCode == null || environmentCode.isBlank()
                || attemptNo < 1 || providerType == null || providerType.isBlank()
                || channelCode == null || channelCode.isBlank() || outcome == null || outcome.isBlank()
                || occurredAt == null) throw new IllegalArgumentException("notification attempt evidence is invalid");
    }
}
