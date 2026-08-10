package com.ftk.tpip.release.domain.model;

import java.time.Instant;

public record NotificationRoutingFailure(long eventId, String eventType, String aggregateType,
        String aggregateId, String environmentCode, String payload, Instant availableAt,
        String routingStatus,String routingError,Instant routingAttemptedAt, Instant createdAt) {}
