package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOperationsApplicationService {
    private static final Duration MAXIMUM_WINDOW = Duration.ofDays(31);
    private final NotificationOutboxRepository outbox;
    private final NotificationDeliveryProperties properties;

    public NotificationOperationsApplicationService(NotificationOutboxRepository outbox,
            NotificationDeliveryProperties properties) {
        this.outbox = outbox;
        this.properties = properties;
        properties.validate();
    }

    @Transactional(readOnly = true)
    public NotificationOperationsSummary summary(Instant fromInclusive, Instant toExclusive,
            String channelCode, NotificationProviderType providerType, Long endpointRevisionId) {
        return summary(fromInclusive, toExclusive, null, channelCode, providerType, endpointRevisionId);
    }

    @Transactional(readOnly = true)
    public NotificationOperationsSummary summary(Instant fromInclusive, Instant toExclusive,
            String environmentCode, String channelCode, NotificationProviderType providerType,
            Long endpointRevisionId) {
        if (fromInclusive == null || toExclusive == null || !fromInclusive.isBefore(toExclusive)
                || Duration.between(fromInclusive, toExclusive).compareTo(MAXIMUM_WINDOW) > 0) {
            throw new IllegalArgumentException("operations window must be positive and no longer than 31 days");
        }
        String channel = optional(channelCode, 100);
        String environment = environment(environmentCode);
        if (endpointRevisionId != null && endpointRevisionId <= 0) {
            throw new IllegalArgumentException("endpointRevisionId must be positive");
        }
        List<NotificationDeliveryAttemptAggregate> values = outbox.aggregateAttempts(
                fromInclusive, toExclusive, environment, channel, providerType, endpointRevisionId);
        long attempts = values.stream().mapToLong(NotificationDeliveryAttemptAggregate::attemptCount).sum();
        long successes = values.stream().mapToLong(NotificationDeliveryAttemptAggregate::successCount).sum();
        long failures = values.stream().mapToLong(NotificationDeliveryAttemptAggregate::failureCount).sum();
        long deadLetters = values.stream().mapToLong(NotificationDeliveryAttemptAggregate::deadLetterCount).sum();
        BigDecimal successRate = attempts == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(successes)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(attempts), 2, RoundingMode.HALF_UP);
        return new NotificationOperationsSummary(fromInclusive, toExclusive, attempts, successes, failures,
                deadLetters, successRate, health(attempts, deadLetters, successRate), List.copyOf(values));
    }

    private String health(long attempts, long deadLetters, BigDecimal successRate) {
        if (attempts < properties.getMinimumOperationalAttempts()) return "INSUFFICIENT_DATA";
        if (successRate.compareTo(properties.getCriticalMinimumSuccessRate()) < 0) return "CRITICAL";
        if (deadLetters > 0 || successRate.compareTo(properties.getWarningMinimumSuccessRate()) < 0) {
            return "WARNING";
        }
        return "HEALTHY";
    }

    private static String optional(String value, int maximumLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maximumLength) throw new IllegalArgumentException("channelCode is too long");
        return normalized;
    }

    static String environment(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (!normalized.matches("[a-z][a-z0-9_-]{0,31}")) {
            throw new IllegalArgumentException("environmentCode is invalid");
        }
        return normalized;
    }
}
