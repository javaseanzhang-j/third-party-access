package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationDeliveryApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");

    @Test
    void claimsWithGovernedLeaseAndSchedulesExponentialRetry() {
        FakeOutbox outbox = new FakeOutbox(task(NotificationDeliveryStatus.PENDING, 0, null));
        var service = service(outbox, 5);

        NotificationDeliveryTask claimed = service.claim("worker-1", 10).getFirst();
        NotificationDeliveryTask failed = service.failed(claimed.id(), "worker-1", "WEBHOOK_HTTP_500\nunsafe");

        assertEquals(NOW.minusSeconds(30), outbox.expiredBefore);
        assertEquals(1, failed.attemptCount());
        assertEquals(NotificationDeliveryStatus.PENDING, failed.status());
        assertEquals(NOW.plusSeconds(5), failed.availableAt());
        assertEquals("WEBHOOK_HTTP_500 unsafe", failed.lastError());
        assertEquals(NotificationFailureClass.TRANSIENT, failed.failureClass());
    }

    @Test
    void deadLettersAtMaximumAttemptsAndAllowsGovernedReplay() {
        FakeOutbox outbox = new FakeOutbox(task(NotificationDeliveryStatus.CLAIMED, 3, "worker-1"));
        var service = service(outbox, 3);

        NotificationDeliveryTask dead = service.failed(7, "worker-1", "WEBHOOK_HTTP_503");
        NotificationDeliveryTask replayed = service.replay(7, "operator-a");

        assertEquals(NotificationDeliveryStatus.DEAD_LETTER, dead.status());
        assertEquals(NOW.plusSeconds(20), dead.availableAt());
        assertEquals(NotificationDeliveryStatus.PENDING, replayed.status());
        assertEquals(0, replayed.attemptCount());
        assertEquals("operator-a", outbox.replayedBy);
    }

    @Test
    void permanentlyRejectsCredentialsWithoutRetryAndHonorsGovernedRetryAfter() {
        FakeOutbox permanentOutbox = new FakeOutbox(task(NotificationDeliveryStatus.CLAIMED, 1, "worker-1"));
        NotificationDeliveryTask permanent = service(permanentOutbox, 5)
                .failed(7, "worker-1", "WECOM_CREDENTIAL_REJECTED", 600L);

        assertEquals(NotificationDeliveryStatus.DEAD_LETTER, permanent.status());
        assertEquals(NotificationFailureClass.PERMANENT, permanent.failureClass());

        FakeOutbox limitedOutbox = new FakeOutbox(task(NotificationDeliveryStatus.CLAIMED, 1, "worker-1"));
        NotificationDeliveryTask limited = service(limitedOutbox, 5)
                .failed(7, "worker-1", "WEBHOOK_HTTP_429", 45L);

        assertEquals(NotificationDeliveryStatus.PENDING, limited.status());
        assertEquals(NotificationFailureClass.RATE_LIMITED, limited.failureClass());
        assertEquals(NOW.plusSeconds(45), limited.availableAt());
        assertEquals(45_000L, limited.lastRetryDelayMillis());
    }

    @Test
    void rejectsBatchOutsideGovernedLimit() {
        var service = service(new FakeOutbox(task(NotificationDeliveryStatus.PENDING, 0, null)), 5);
        assertThrows(IllegalArgumentException.class,
                () -> service.claim("worker-1", 101));
    }

    @Test
    void exposesAndReroutesNoMatchEvents() {
        FakeOutbox outbox = new FakeOutbox(task(NotificationDeliveryStatus.PENDING, 0, null));
        outbox.routingFailure = new com.ftk.tpip.release.domain.model.NotificationRoutingFailure(91,
                "UNMATCHED_EVENT", "ORDER", "A-1", "prod", "{}", NOW.minusSeconds(10),
                "NO_MATCH", null, NOW, NOW.minusSeconds(20));
        var service = service(outbox, 5);

        assertEquals(91, service.routingFailures(10).getFirst().eventId());
        assertEquals(91, service.reroute(91, "operator-a").eventId());
        assertEquals("operator-a", outbox.reroutedBy);
    }

    @Test
    void batchReplayRequiresUniqueIdsAndDelegatesOneGovernedOperation() {
        FakeOutbox outbox = new FakeOutbox(task(NotificationDeliveryStatus.DEAD_LETTER, 3, null));
        var service = service(outbox, 5);

        List<NotificationDeliveryTask> replayed = service.replayBatch(List.of(7L), "operator-a");

        assertEquals(1, replayed.size());
        assertEquals("operator-a", outbox.replayedBy);
        assertThrows(IllegalArgumentException.class,
                () -> service.replayBatch(List.of(7L, 7L), "operator-a"));
    }

    private static NotificationDeliveryApplicationService service(FakeOutbox outbox, int maximumAttempts) {
        var properties = new NotificationDeliveryProperties();
        properties.setClaimLease(Duration.ofSeconds(30));
        properties.setRetryBaseDelay(Duration.ofSeconds(5));
        properties.setRetryMaximumDelay(Duration.ofSeconds(20));
        properties.setMaximumAttempts(maximumAttempts);
        properties.setMaximumBatchSize(100);
        return new NotificationDeliveryApplicationService(outbox, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static NotificationDeliveryTask task(NotificationDeliveryStatus status, int attempts, String worker) {
        return new NotificationDeliveryTask(7, 70, "ops-primary",
                3L, NotificationProviderType.WEBHOOK, "https://notify.example.test/events", null, null,
                "{}", null, null, null,
                "TPIP_HEALTH_ALERT_OPENED", "DEPLOYMENT_HEALTH_ALERT", "19",
                "{\"severity\":\"WARNING\"}", status, attempts, NOW.minusSeconds(1), worker,
                worker == null ? null : NOW.minusSeconds(1), null, null,
                null, null, null, NOW.minusSeconds(60), NOW.minusSeconds(1));
    }

    private static final class FakeOutbox implements NotificationOutboxRepository {
        private NotificationDeliveryTask value;
        private Instant expiredBefore;
        private String replayedBy;
        private String reroutedBy;
        private com.ftk.tpip.release.domain.model.NotificationRoutingFailure routingFailure;

        private FakeOutbox(NotificationDeliveryTask value) { this.value = value; }

        @Override public NotificationOutboxMessage enqueue(NotificationOutboxMessage message) {
            throw new UnsupportedOperationException();
        }

        @Override public List<NotificationDeliveryTask> claim(String workerId, Instant now,
                Instant expiredBefore, int batchSize) {
            this.expiredBefore = expiredBefore;
            value = copy(NotificationDeliveryStatus.CLAIMED, value.attemptCount() + 1, now,
                    workerId, now, null, value.lastError(), value.failureClass(),
                    value.lastRetryDelayMillis(), value.deadLetteredAt(), now);
            return List.of(value);
        }

        @Override public Optional<NotificationDeliveryTask> findById(long id) {
            return value.id() == id ? Optional.of(value) : Optional.empty();
        }

        @Override public NotificationDeliveryTask markDelivered(long id, String workerId, Instant deliveredAt) {
            value = copy(NotificationDeliveryStatus.DELIVERED, value.attemptCount(), value.availableAt(),
                    null, null, deliveredAt, null, null, null, null, deliveredAt);
            return value;
        }

        @Override public NotificationDeliveryTask markFailed(long id, String workerId, Instant availableAt,
                String error, NotificationFailureClass failureClass, long retryDelayMillis, boolean deadLetter,
                Instant failedAt) {
            NotificationDeliveryStatus status = deadLetter
                    ? NotificationDeliveryStatus.DEAD_LETTER : NotificationDeliveryStatus.PENDING;
            value = copy(status, value.attemptCount(), availableAt, null, null, null, error, failureClass,
                    retryDelayMillis, deadLetter ? failedAt : null, NOW);
            return value;
        }

        @Override public List<NotificationDeliveryTask> findByStatus(NotificationDeliveryStatus status, int limit) {
            List<NotificationDeliveryTask> result = new ArrayList<>();
            if (value.status() == status) result.add(value);
            return result;
        }

        @Override public NotificationDeliveryTask replay(long id, Instant availableAt, String actor) {
            replayedBy = actor;
            value = copy(NotificationDeliveryStatus.PENDING, 0, availableAt, null, null, null, null,
                    null, null, null, availableAt);
            return value;
        }
        @Override public List<com.ftk.tpip.release.domain.model.NotificationRoutingFailure> findRoutingFailures(int limit) { return routingFailure == null ? List.of() : List.of(routingFailure); }
        @Override public com.ftk.tpip.release.domain.model.NotificationRoutingFailure reroute(long id,String actor) { reroutedBy=actor; return routingFailure; }
        @Override public long countRoutingFailures() { return routingFailure == null ? 0 : 1; }
        @Override public List<NotificationDeliveryTask> replayBatch(List<Long> ids, Instant at, String actor) {
            return ids.stream().map(id -> replay(id, at, actor)).toList();
        }
        @Override public List<com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate> aggregateAttempts(
                Instant from, Instant to, String environment, String channel,
                NotificationProviderType provider, Long endpoint) {
            return List.of();
        }

        private NotificationDeliveryTask copy(NotificationDeliveryStatus status, int attempts, Instant availableAt,
                String claimedBy, Instant claimedAt, Instant deliveredAt, String error,
                NotificationFailureClass failureClass, Long retryDelayMillis, Instant deadLetteredAt,
                Instant updatedAt) {
            return new NotificationDeliveryTask(value.id(), value.eventId(), value.channelCode(),
                    value.channelVersionId(), value.providerType(), value.endpointUri(),
                    value.endpointRevisionId(), value.authorizationSecretRef(),
                    value.providerConfiguration(),
                    value.templateVersionId(), value.messageContentType(), value.messagePayload(),
                    value.eventType(), value.aggregateType(),
                    value.aggregateId(), value.payload(), status, attempts, availableAt, claimedBy, claimedAt,
                    deliveredAt, error, failureClass, retryDelayMillis, deadLetteredAt,
                    value.createdAt(), updatedAt);
        }
    }
}
