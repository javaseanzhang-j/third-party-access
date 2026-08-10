package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.model.NotificationRoutingFailure;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationOperationsApplicationServiceTest {
    private static final Instant FROM = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void aggregatesImmutableAttemptsAndEvaluatesGovernedHealth() {
        StubOutbox outbox = new StubOutbox(List.of(
                new NotificationDeliveryAttemptAggregate("default", NotificationProviderType.WEBHOOK,
                        "ops-primary", 42L, 60, 58, 2, 0),
                new NotificationDeliveryAttemptAggregate("default", NotificationProviderType.WECOM,
                        "ops-secondary", 43L, 40, 36, 4, 1)));
        NotificationOperationsSummary result = new NotificationOperationsApplicationService(
                outbox, new NotificationDeliveryProperties()).summary(
                FROM, FROM.plusSeconds(86400), null, null, null);

        assertEquals(100, result.attemptCount());
        assertEquals(94, result.successCount());
        assertEquals(6, result.failureCount());
        assertEquals(1, result.deadLetterCount());
        assertEquals(new BigDecimal("94.00"), result.successRate());
        assertEquals("CRITICAL", result.healthStatus());
        assertEquals(2, result.breakdowns().size());
    }

    @Test
    void marksSmallSamplesInsufficientAndRejectsUnboundedWindows() {
        StubOutbox outbox = new StubOutbox(List.of(
                new NotificationDeliveryAttemptAggregate("default", NotificationProviderType.WEBHOOK,
                        "ops-primary", null, 2, 2, 0, 0)));
        var service = new NotificationOperationsApplicationService(outbox, new NotificationDeliveryProperties());

        assertEquals("INSUFFICIENT_DATA",
                service.summary(FROM, FROM.plusSeconds(60), null, null, null).healthStatus());
        assertThrows(IllegalArgumentException.class,
                () -> service.summary(FROM, FROM.plusSeconds(32L * 86400), null, null, null));
    }

    private static final class StubOutbox implements NotificationOutboxRepository {
        private final List<NotificationDeliveryAttemptAggregate> values;
        StubOutbox(List<NotificationDeliveryAttemptAggregate> values) { this.values = values; }
        @Override public List<NotificationDeliveryAttemptAggregate> aggregateAttempts(Instant from, Instant to,
                String environment, String channel, NotificationProviderType provider, Long endpoint) { return values; }
        @Override public NotificationOutboxMessage enqueue(NotificationOutboxMessage value) { throw unsupported(); }
        @Override public List<NotificationDeliveryTask> claim(String worker,Instant now,Instant expired,int size) { throw unsupported(); }
        @Override public Optional<NotificationDeliveryTask> findById(long id) { return Optional.empty(); }
        @Override public NotificationDeliveryTask markDelivered(long id,String worker,Instant at) { throw unsupported(); }
        @Override public NotificationDeliveryTask markFailed(long id,String worker,Instant available,String error,
                NotificationFailureClass failure,long delay,boolean dead,Instant at) { throw unsupported(); }
        @Override public List<NotificationDeliveryTask> findByStatus(NotificationDeliveryStatus status,int limit) { return List.of(); }
        @Override public NotificationDeliveryTask replay(long id,Instant at,String actor) { throw unsupported(); }
        @Override public List<NotificationDeliveryTask> replayBatch(List<Long> ids,Instant at,String actor) { throw unsupported(); }
        @Override public List<NotificationRoutingFailure> findRoutingFailures(int limit) { return List.of(); }
        @Override public NotificationRoutingFailure reroute(long id,String actor) { throw unsupported(); }
        @Override public long countRoutingFailures() { return 0; }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }
}
