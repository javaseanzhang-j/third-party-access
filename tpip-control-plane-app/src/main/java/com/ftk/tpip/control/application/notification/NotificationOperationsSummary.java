package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record NotificationOperationsSummary(Instant fromInclusive, Instant toExclusive,
        long attemptCount, long successCount, long failureCount, long deadLetterCount,
        BigDecimal successRate, String healthStatus, List<NotificationDeliveryAttemptAggregate> breakdowns) {}
