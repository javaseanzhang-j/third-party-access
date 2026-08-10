package com.ftk.tpip.worker.notification;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record NotificationTask(long id, long eventId, String channelCode,
        Long channelVersionId, String providerType, String endpointUri, Long endpointRevisionId, String authorizationSecretRef,
        JsonNode providerConfiguration, Long templateVersionId, String messageContentType, JsonNode messagePayload,
        String eventType, String aggregateType, String aggregateId,
        JsonNode payload, String status, int attemptCount, Instant availableAt, String claimedBy,
        Instant claimedAt, Instant deliveredAt, String lastError, String failureClass,
        Long lastRetryDelayMillis, Instant deadLetteredAt, Instant createdAt, Instant updatedAt) {}
