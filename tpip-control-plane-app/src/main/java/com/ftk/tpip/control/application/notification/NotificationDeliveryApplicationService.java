package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import com.ftk.tpip.release.domain.model.NotificationRoutingFailure;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationDeliveryApplicationService {
    private final NotificationOutboxRepository outbox;
    private final NotificationDeliveryProperties properties;
    private final Clock clock;
    private final NotificationFailureClassifier failures;

    @Autowired
    public NotificationDeliveryApplicationService(NotificationOutboxRepository outbox,
            NotificationDeliveryProperties properties, NotificationFailureClassifier failures) {
        this(outbox, properties, failures, Clock.systemUTC());
    }

    NotificationDeliveryApplicationService(NotificationOutboxRepository outbox,
            NotificationDeliveryProperties properties, Clock clock) {
        this(outbox, properties, new NotificationFailureClassifier(), clock);
    }

    NotificationDeliveryApplicationService(NotificationOutboxRepository outbox,
            NotificationDeliveryProperties properties, NotificationFailureClassifier failures, Clock clock) {
        this.outbox = outbox;
        this.properties = properties;
        this.failures = failures;
        this.clock = clock;
        properties.validate();
    }

    @Transactional
    public List<NotificationDeliveryTask> claim(String workerId, int batchSize) {
        String worker = required(workerId, "workerId", 100);
        if (batchSize < 1 || batchSize > properties.getMaximumBatchSize()) {
            throw new IllegalArgumentException("batchSize is outside the governed limit");
        }
        Instant now = clock.instant();
        return outbox.claim(worker, now, now.minus(properties.getClaimLease()), batchSize);
    }

    @Transactional
    public NotificationDeliveryTask delivered(long id, String workerId) {
        requireExisting(id);
        return outbox.markDelivered(id, required(workerId, "workerId", 100), clock.instant());
    }

    @Transactional
    public NotificationDeliveryTask failed(long id, String workerId, String error) {
        return failed(id, workerId, error, null);
    }

    @Transactional
    public NotificationDeliveryTask failed(long id, String workerId, String error, Long retryAfterSeconds) {
        NotificationDeliveryTask current = requireExisting(id);
        String safeError = required(error, "error", 1000);
        NotificationFailureClass failureClass = failures.classify(safeError);
        Duration delay = failureClass == NotificationFailureClass.PERMANENT
                ? Duration.ZERO : governedDelay(current.attemptCount(), retryAfterSeconds);
        boolean deadLetter = failureClass == NotificationFailureClass.PERMANENT
                || current.attemptCount() >= properties.getMaximumAttempts();
        Instant now = clock.instant();
        return outbox.markFailed(id, required(workerId, "workerId", 100), now.plus(delay), safeError,
                failureClass, delay.toMillis(), deadLetter, now);
    }

    @Transactional(readOnly = true)
    public List<NotificationDeliveryTask> list(NotificationDeliveryStatus status, int limit) {
        if (limit < 1 || limit > properties.getMaximumBatchSize()) {
            throw new IllegalArgumentException("limit is outside the governed range");
        }
        return outbox.findByStatus(status, limit);
    }

    @Transactional
    public NotificationDeliveryTask replay(long id, String actor) {
        requireExisting(id);
        return outbox.replay(id, clock.instant(), required(actor, "actor", 100));
    }

    @Transactional
    public List<NotificationDeliveryTask> replayBatch(List<Long> ids, String actor) {
        if (ids == null || ids.isEmpty() || ids.size() > properties.getMaximumBatchSize()) {
            throw new IllegalArgumentException("delivery ids are outside the governed batch range");
        }
        java.util.LinkedHashSet<Long> distinct = new java.util.LinkedHashSet<>(ids);
        if (distinct.size() != ids.size() || distinct.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("delivery ids must be unique and positive");
        }
        return outbox.replayBatch(List.copyOf(distinct), clock.instant(), required(actor, "actor", 100));
    }

    @Transactional(readOnly = true)
    public List<NotificationRoutingFailure> routingFailures(int limit) {
        if (limit < 1 || limit > properties.getMaximumBatchSize()) {
            throw new IllegalArgumentException("limit is outside the governed range");
        }
        return outbox.findRoutingFailures(limit);
    }

    @Transactional
    public NotificationRoutingFailure reroute(long eventId, String actor) {
        if (eventId <= 0) throw new IllegalArgumentException("eventId must be positive");
        return outbox.reroute(eventId, required(actor, "actor", 100));
    }

    private NotificationDeliveryTask requireExisting(long id) {
        return outbox.findById(id).orElseThrow(() -> new NotificationOutboxNotFoundException(id));
    }

    private Duration retryDelay(int attemptCount) {
        long base = properties.getRetryBaseDelay().toMillis();
        long maximum = properties.getRetryMaximumDelay().toMillis();
        int exponent = Math.max(0, Math.min(attemptCount - 1, 30));
        long multiplier = 1L << exponent;
        long delay = base > Long.MAX_VALUE / multiplier ? maximum : base * multiplier;
        return Duration.ofMillis(Math.min(delay, maximum));
    }

    private Duration governedDelay(int attemptCount, Long retryAfterSeconds) {
        Duration exponential = retryDelay(attemptCount);
        if (retryAfterSeconds == null) return exponential;
        if (retryAfterSeconds < 0) throw new IllegalArgumentException("retryAfterSeconds must not be negative");
        Duration hint = Duration.ofSeconds(retryAfterSeconds)
                .compareTo(properties.getMaximumRetryAfter()) > 0
                ? properties.getMaximumRetryAfter() : Duration.ofSeconds(retryAfterSeconds);
        return hint.compareTo(exponential) > 0 ? hint : exponential;
    }

    private static String required(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.length() > maximumLength) normalized = normalized.substring(0, maximumLength);
        return normalized;
    }
}
