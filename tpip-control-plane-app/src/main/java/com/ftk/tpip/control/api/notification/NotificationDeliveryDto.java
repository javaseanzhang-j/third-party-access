package com.ftk.tpip.control.api.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import java.time.Instant;

record NotificationDeliveryDto(long id, long eventId, String channelCode,
        Long channelVersionId, String providerType, String endpointUri, Long endpointRevisionId, String authorizationSecretRef,
        JsonNode providerConfiguration, Long templateVersionId, String messageContentType, JsonNode messagePayload,
        String eventType, String aggregateType, String aggregateId,
        JsonNode payload, NotificationDeliveryStatus status, int attemptCount, Instant availableAt,
        String claimedBy, Instant claimedAt, Instant deliveredAt, String lastError,
        String failureClass, Long lastRetryDelayMillis, Instant deadLetteredAt,
        Instant createdAt, Instant updatedAt) {
    static NotificationDeliveryDto from(NotificationDeliveryTask value, ObjectMapper json) {
        try {
            return new NotificationDeliveryDto(value.id(), value.eventId(), value.channelCode(),
                    value.channelVersionId(), value.providerType().name(), value.endpointUri(), value.endpointRevisionId(),
                    value.authorizationSecretRef(),
                    json.readTree(value.providerConfiguration()),
                    value.templateVersionId(), value.messageContentType(),
                    value.messagePayload() == null ? null : json.readTree(value.messagePayload()),
                    value.eventType(), value.aggregateType(),
                    value.aggregateId(), json.readTree(value.payload()), value.status(), value.attemptCount(),
                    value.availableAt(), value.claimedBy(), value.claimedAt(), value.deliveredAt(),
                    value.lastError(), value.failureClass() == null ? null : value.failureClass().name(),
                    value.lastRetryDelayMillis(), value.deadLetteredAt(), value.createdAt(), value.updatedAt());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored notification payload is invalid", exception);
        }
    }
}
