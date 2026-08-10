package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationDeliveryTask(long id, long eventId, String channelCode,
        Long channelVersionId, NotificationProviderType providerType, String endpointUri, Long endpointRevisionId,
        String authorizationSecretRef, String providerConfiguration, Long templateVersionId,
        String messageContentType, String messagePayload,
        String eventType, String aggregateType, String aggregateId,
        String payload, NotificationDeliveryStatus status, int attemptCount, Instant availableAt,
        String claimedBy, Instant claimedAt, Instant deliveredAt, String lastError,
        NotificationFailureClass failureClass, Long lastRetryDelayMillis, Instant deadLetteredAt,
        Instant createdAt, Instant updatedAt) {
    public NotificationDeliveryTask {
        if (id <= 0) throw new IllegalArgumentException("id must be positive");
        if (eventId <= 0) throw new IllegalArgumentException("eventId must be positive");
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount must not be negative");
        if (lastRetryDelayMillis != null && lastRetryDelayMillis < 0) {
            throw new IllegalArgumentException("lastRetryDelayMillis must not be negative");
        }
        if (channelVersionId == null || channelVersionId <= 0 || providerType == null
                || endpointUri == null || endpointUri.isBlank()
                || channelCode == null || channelCode.isBlank() || eventType == null || eventType.isBlank()
                || aggregateType == null || aggregateType.isBlank()
                || aggregateId == null || aggregateId.isBlank() || status == null || availableAt == null) {
            throw new IllegalArgumentException("notification delivery task is incomplete");
        }
        payload = payload == null || payload.isBlank() ? "{}" : payload;
        providerConfiguration = providerConfiguration == null || providerConfiguration.isBlank() ? "{}" : providerConfiguration;
        messagePayload = messagePayload == null || messagePayload.isBlank() ? null : messagePayload;
    }
}
