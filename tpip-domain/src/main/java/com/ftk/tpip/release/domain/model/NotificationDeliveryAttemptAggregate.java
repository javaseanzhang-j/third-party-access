package com.ftk.tpip.release.domain.model;

public record NotificationDeliveryAttemptAggregate(String environmentCode, NotificationProviderType providerType, String channelCode,
        Long endpointRevisionId, long attemptCount, long successCount, long failureCount,
        long deadLetterCount) {
    public NotificationDeliveryAttemptAggregate {
        if (environmentCode == null || environmentCode.isBlank() || providerType == null || channelCode == null || channelCode.isBlank()
                || attemptCount < 0 || successCount < 0 || failureCount < 0 || deadLetterCount < 0
                || successCount + failureCount != attemptCount || deadLetterCount > failureCount) {
            throw new IllegalArgumentException("notification attempt aggregate is invalid");
        }
    }
}
