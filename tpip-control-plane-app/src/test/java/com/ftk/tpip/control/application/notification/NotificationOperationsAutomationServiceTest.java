package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import com.ftk.tpip.release.domain.model.NotificationFailureClass;
import com.ftk.tpip.release.domain.model.NotificationOperationsAlert;
import com.ftk.tpip.release.domain.model.NotificationOperationsEvaluation;
import com.ftk.tpip.release.domain.model.NotificationOperationsPolicyVersion;
import com.ftk.tpip.release.domain.model.NotificationMaintenanceWindow;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import com.ftk.tpip.release.domain.model.NotificationRoutingFailure;
import com.ftk.tpip.release.domain.repository.NotificationOperationsRepository;
import com.ftk.tpip.release.domain.repository.NotificationOperationsGovernanceRepository;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NotificationOperationsAutomationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-08T12:30:00Z");

    @Test
    void governsIdempotentAlertEscalationAcknowledgementAndRecovery() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        FakeOutbox outbox = new FakeOutbox();
        FakeOperations operations = new FakeOperations();
        ObjectMapper mapper = new ObjectMapper();
        var governanceRepository = new FakeGovernance();
        var governance = new NotificationOperationsGovernanceService(governanceRepository,
                new CanonicalJsonService(mapper), mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        var service = new NotificationOperationsAutomationService(
                new NotificationOperationsApplicationService(outbox, properties), operations, outbox,
                new CanonicalJsonService(mapper), mapper, properties, governance,
                Clock.fixed(NOW, ZoneOffset.UTC));
        Instant start = NOW.minus(Duration.ofMinutes(20));

        outbox.aggregate = aggregate(20, 19, 1, 0);
        NotificationOperationsEvaluation warning = service.evaluate("default", start,
                start.plus(Duration.ofMinutes(5)), "scheduler");
        assertEquals("WARNING", warning.healthStatus());
        assertEquals(1, outbox.events.size());
        assertSame(warning, service.evaluate("default", start, start.plus(Duration.ofMinutes(5)), "scheduler"));
        assertEquals(1, outbox.events.size());

        outbox.aggregate = aggregate(20, 18, 2, 0);
        service.evaluate("default", start.plus(Duration.ofMinutes(5)), start.plus(Duration.ofMinutes(10)), "scheduler");
        assertEquals(List.of("TPIP_NOTIFICATION_OPERATIONS_ALERT_OPENED",
                        "TPIP_NOTIFICATION_OPERATIONS_ALERT_RESOLVED",
                        "TPIP_NOTIFICATION_OPERATIONS_ALERT_OPENED"),
                outbox.events.stream().map(NotificationOutboxMessage::eventType).toList());
        NotificationOperationsAlert critical = operations.findActiveAlert("default").orElseThrow();
        assertEquals("CRITICAL", critical.severity());

        service.acknowledgeAlert("default", critical.id(), "operator-a");
        assertEquals("ACKNOWLEDGED", operations.findAlert(critical.id()).orElseThrow().status());

        outbox.aggregate = aggregate(20, 20, 0, 0);
        service.evaluate("default", start.plus(Duration.ofMinutes(10)), start.plus(Duration.ofMinutes(15)), "scheduler");
        assertEquals("RESOLVED", operations.findAlert(critical.id()).orElseThrow().status());
        assertEquals(List.of("TPIP_NOTIFICATION_OPERATIONS_ALERT_ACKNOWLEDGED",
                        "TPIP_NOTIFICATION_OPERATIONS_ALERT_RESOLVED"),
                outbox.events.subList(3, 5).stream().map(NotificationOutboxMessage::eventType).toList());
    }

    @Test
    void alignsToLastCompletedDelayedWindow() {
        assertEquals(Instant.parse("2026-08-08T12:25:00Z"),
                NotificationOperationsScheduler.completedWindowEnd(
                        Instant.parse("2026-08-08T12:30:20Z"), Duration.ofMinutes(5), Duration.ofSeconds(30)));
    }

    @Test
    void preservesCriticalEvaluationButSuppressesAlertDuringMaintenance() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        FakeOutbox outbox = new FakeOutbox(); outbox.aggregate = aggregate(20, 18, 2, 0);
        FakeOperations operations = new FakeOperations(); FakeGovernance repository = new FakeGovernance();
        Instant start = NOW.minus(Duration.ofMinutes(5));
        repository.overlapping = new NotificationMaintenanceWindow(7L, "default", start, NOW,
                "planned change", "SCHEDULED", "operator", null, null, NOW, NOW);
        ObjectMapper mapper = new ObjectMapper();
        var governance = new NotificationOperationsGovernanceService(repository, new CanonicalJsonService(mapper),
                mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        var service = new NotificationOperationsAutomationService(
                new NotificationOperationsApplicationService(outbox, properties), operations, outbox,
                new CanonicalJsonService(mapper), mapper, properties, governance, Clock.fixed(NOW, ZoneOffset.UTC));

        var evaluation = service.evaluate("default", start, NOW, "scheduler");

        assertEquals("CRITICAL", evaluation.healthStatus());
        assertEquals(0, outbox.events.size());
        assertEquals(true, evaluation.thresholdSnapshot().contains("\"alertSuppressed\":true"));
    }

    @Test
    void escalatesUnacknowledgedCriticalAlertOnlyOnce() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        FakeOutbox outbox = new FakeOutbox(); FakeOperations operations = new FakeOperations();
        FakeGovernance repository = new FakeGovernance();
        repository.published = policy(Duration.ofMinutes(1));
        operations.alerts.put(9L, new NotificationOperationsAlert(9L, "default", 1L, "OPS_CRITICAL",
                "CRITICAL", "OPEN", "critical", "{}", null, null, null, null,
                null, NOW.minus(Duration.ofMinutes(31)), NOW.minus(Duration.ofMinutes(31))));
        ObjectMapper mapper = new ObjectMapper();
        var governance = new NotificationOperationsGovernanceService(repository, new CanonicalJsonService(mapper),
                mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));
        var service = new NotificationOperationsAutomationService(
                new NotificationOperationsApplicationService(outbox, properties), operations, outbox,
                new CanonicalJsonService(mapper), mapper, properties, governance, Clock.fixed(NOW, ZoneOffset.UTC));

        assertEquals(1, service.escalateDueAlerts("default"));
        assertEquals(0, service.escalateDueAlerts("default"));
        assertEquals("TPIP_NOTIFICATION_OPERATIONS_ALERT_ESCALATED", outbox.events.getFirst().eventType());
        assertEquals(1, service.repeatDueAlerts("default"));
        assertEquals(0, service.repeatDueAlerts("default"));
        assertEquals("TPIP_NOTIFICATION_OPERATIONS_ALERT_REPEATED", outbox.events.get(1).eventType());
    }

    @Test
    void governsImmutablePolicyPublicationAndNonOverlappingMaintenance() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        FakeGovernance repository = new FakeGovernance(); ObjectMapper mapper = new ObjectMapper();
        var service = new NotificationOperationsGovernanceService(repository, new CanonicalJsonService(mapper),
                mapper, properties, Clock.fixed(NOW, ZoneOffset.UTC));

        var draft = service.createPolicyVersion("default", 30, new BigDecimal("99.50"),
                new BigDecimal("97.00"), Duration.ofMinutes(10), Duration.ofHours(1), "operator");
        assertEquals("DRAFT", draft.lifecycleStatus());
        assertEquals("PUBLISHED", service.publishPolicyVersion(draft.id(), "publisher").lifecycleStatus());
        Instant start = NOW.plus(Duration.ofHours(1));
        var window = service.scheduleMaintenance("default", start, start.plus(Duration.ofHours(2)),
                "planned provider change", "operator");
        assertThrows(IllegalArgumentException.class, () -> service.scheduleMaintenance("default",
                start.plusSeconds(1), start.plus(Duration.ofHours(3)), "overlap", "operator"));
        assertEquals("CANCELLED", service.cancelMaintenance(window.id(), "operator").status());
    }

    private static NotificationOperationsPolicyVersion policy(Duration escalation) {
        return new NotificationOperationsPolicyVersion(3L, "default", 2, 20, new BigDecimal("99.00"),
                new BigDecimal("95.00"), escalation, Duration.ofMinutes(30), "PUBLISHED", "0".repeat(64),
                "operator", "operator", NOW.minusSeconds(60), NOW.minusSeconds(120));
    }

    private static List<NotificationDeliveryAttemptAggregate> aggregate(long attempts, long successes,
            long failures, long deadLetters) {
        return List.of(new NotificationDeliveryAttemptAggregate("default", NotificationProviderType.WEBHOOK,
                "ops", 1L, attempts, successes, failures, deadLetters));
    }

    private static final class FakeOperations implements NotificationOperationsRepository {
        private final Map<String, NotificationOperationsEvaluation> evaluations = new LinkedHashMap<>();
        private final Map<Long, NotificationOperationsAlert> alerts = new LinkedHashMap<>();
        private long evaluationSequence;
        private long alertSequence;

        @Override public Optional<NotificationOperationsEvaluation> findEvaluation(String environment,
                Instant start, Instant end) { return Optional.ofNullable(evaluations.get(key(environment, start, end))); }
        @Override public boolean createEvaluationIfAbsent(NotificationOperationsEvaluation value) {
            String key = key(value.environmentCode(), value.windowStart(), value.windowEnd());
            if (evaluations.containsKey(key)) return false;
            var saved = new NotificationOperationsEvaluation(++evaluationSequence, value.environmentCode(),
                    value.windowStart(), value.windowEnd(), value.attemptCount(), value.successCount(),
                    value.failureCount(), value.deadLetterCount(), value.successRate(), value.healthStatus(),
                    value.thresholdSnapshot(), value.evaluatedBy(), value.evaluatedAt());
            evaluations.put(key, saved);
            return true;
        }
        @Override public List<NotificationOperationsEvaluation> findEvaluations(String environment, int limit) {
            return evaluations.values().stream().filter(v -> v.environmentCode().equals(environment)).limit(limit).toList();
        }
        @Override public Optional<NotificationOperationsAlert> findAlert(long id) { return Optional.ofNullable(alerts.get(id)); }
        @Override public Optional<NotificationOperationsAlert> findActiveAlert(String environment) {
            return alerts.values().stream().filter(v -> v.environmentCode().equals(environment)
                    && !"RESOLVED".equals(v.status())).reduce((first, second) -> second);
        }
        @Override public NotificationOperationsAlert createAlert(NotificationOperationsAlert value) {
            Instant now = NOW;
            var saved = copy(value, ++alertSequence, value.status(), value.acknowledgedBy(),
                    value.acknowledgedAt(), value.resolvedAt(), now);
            alerts.put(saved.id(), saved);
            return saved;
        }
        @Override public NotificationOperationsAlert acknowledgeAlert(long id, String actor, Instant at) {
            NotificationOperationsAlert current = alerts.get(id);
            if (!"OPEN".equals(current.status())) throw new IllegalArgumentException();
            NotificationOperationsAlert saved = copy(current, id, "ACKNOWLEDGED", actor, at, null, at);
            alerts.put(id, saved);
            return saved;
        }
        @Override public Optional<NotificationOperationsAlert> markEscalated(long id, Instant at) {
            NotificationOperationsAlert current = alerts.get(id);
            if (current == null || !"OPEN".equals(current.status()) || !"CRITICAL".equals(current.severity())
                    || current.escalatedAt() != null) return Optional.empty();
            var saved = new NotificationOperationsAlert(current.id(), current.environmentCode(), current.evaluationId(),
                    current.alertCode(), current.severity(), current.status(), current.summary(), current.details(),
                    current.acknowledgedBy(), current.acknowledgedAt(), current.resolvedAt(), at,
                    current.lastNotifiedAt(), current.createdAt(), at);
            alerts.put(id, saved); return Optional.of(saved);
        }
        @Override public Optional<NotificationOperationsAlert> markRepeatNotified(long id, Instant eligibleBefore,
                Instant at) {
            NotificationOperationsAlert current = alerts.get(id);
            Instant baseline = current == null || current.lastNotifiedAt() == null
                    ? current == null ? null : current.createdAt() : current.lastNotifiedAt();
            if (current == null || !"OPEN".equals(current.status()) || baseline == null
                    || baseline.isAfter(eligibleBefore)) return Optional.empty();
            var saved = new NotificationOperationsAlert(current.id(), current.environmentCode(), current.evaluationId(),
                    current.alertCode(), current.severity(), current.status(), current.summary(), current.details(),
                    current.acknowledgedBy(), current.acknowledgedAt(), current.resolvedAt(), current.escalatedAt(),
                    at, current.createdAt(), at);
            alerts.put(id, saved); return Optional.of(saved);
        }
        @Override public List<NotificationOperationsAlert> resolveActiveAlerts(String environment, Instant at) {
            List<NotificationOperationsAlert> resolved = new ArrayList<>();
            for (NotificationOperationsAlert value : List.copyOf(alerts.values())) {
                if (value.environmentCode().equals(environment) && !"RESOLVED".equals(value.status())) {
                    NotificationOperationsAlert saved = copy(value, value.id(), "RESOLVED", value.acknowledgedBy(),
                            value.acknowledgedAt(), at, at);
                    alerts.put(saved.id(), saved);
                    resolved.add(saved);
                }
            }
            return resolved;
        }
        @Override public List<NotificationOperationsAlert> findAlerts(String environment, int limit) {
            return alerts.values().stream().filter(v -> v.environmentCode().equals(environment)).limit(limit).toList();
        }
        private static String key(String environment, Instant start, Instant end) { return environment + start + end; }
        private static NotificationOperationsAlert copy(NotificationOperationsAlert value, long id, String status,
                String actor, Instant acknowledgedAt, Instant resolvedAt, Instant updatedAt) {
            return new NotificationOperationsAlert(id, value.environmentCode(), value.evaluationId(),
                    value.alertCode(), value.severity(), status, value.summary(), value.details(), actor,
                    acknowledgedAt, resolvedAt, value.escalatedAt(),
                    value.lastNotifiedAt(), value.createdAt() == null ? NOW : value.createdAt(), updatedAt);
        }
    }

    private static final class FakeOutbox implements NotificationOutboxRepository {
        private List<NotificationDeliveryAttemptAggregate> aggregate = List.of();
        private final List<NotificationOutboxMessage> events = new ArrayList<>();
        @Override public NotificationOutboxMessage enqueue(NotificationOutboxMessage value) {
            NotificationOutboxMessage saved = new NotificationOutboxMessage((long) events.size() + 1,
                    value.eventType(), value.aggregateType(), value.aggregateId(), value.environmentCode(),
                    value.payload(), value.availableAt(), NOW);
            events.add(saved); return saved;
        }
        @Override public List<NotificationDeliveryAttemptAggregate> aggregateAttempts(Instant from, Instant to,
                String environment, String channel, NotificationProviderType provider, Long endpoint) { return aggregate; }
        @Override public List<NotificationDeliveryTask> claim(String worker, Instant now, Instant expired, int size) { return List.of(); }
        @Override public Optional<NotificationDeliveryTask> findById(long id) { return Optional.empty(); }
        @Override public NotificationDeliveryTask markDelivered(long id, String worker, Instant at) { throw unsupported(); }
        @Override public NotificationDeliveryTask markFailed(long id, String worker, Instant available, String error,
                NotificationFailureClass failure, long delay, boolean dead, Instant at) { throw unsupported(); }
        @Override public List<NotificationDeliveryTask> findByStatus(NotificationDeliveryStatus status, int limit) { return List.of(); }
        @Override public NotificationDeliveryTask replay(long id, Instant at, String actor) { throw unsupported(); }
        @Override public List<NotificationDeliveryTask> replayBatch(List<Long> ids, Instant at, String actor) { return List.of(); }
        @Override public List<NotificationRoutingFailure> findRoutingFailures(int limit) { return List.of(); }
        @Override public NotificationRoutingFailure reroute(long id, String actor) { throw unsupported(); }
        @Override public long countRoutingFailures() { return 0; }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }

    private static final class FakeGovernance implements NotificationOperationsGovernanceRepository {
        private NotificationOperationsPolicyVersion published;
        private NotificationMaintenanceWindow overlapping;
        private final Map<Long, NotificationOperationsPolicyVersion> policies = new LinkedHashMap<>();
        private final Map<Long, NotificationMaintenanceWindow> windows = new LinkedHashMap<>();
        @Override public void lockEnvironment(String environment) { }
        @Override public NotificationOperationsPolicyVersion createPolicyVersion(NotificationOperationsPolicyVersion value) {
            long id = policies.size() + 1L;
            var saved = new NotificationOperationsPolicyVersion(id, value.environmentCode(), policies.size() + 1,
                    value.minimumOperationalAttempts(), value.warningMinimumSuccessRate(),
                    value.criticalMinimumSuccessRate(), value.criticalEscalationAfter(),
                    value.repeatNotificationAfter(), "DRAFT", value.contentChecksum(), value.createdBy(),
                    null, null, NOW);
            policies.put(id, saved); return saved;
        }
        @Override public Optional<NotificationOperationsPolicyVersion> findPolicyVersion(long id) { return Optional.ofNullable(policies.get(id)); }
        @Override public Optional<NotificationOperationsPolicyVersion> findPublishedPolicy(String environment) { return Optional.ofNullable(published); }
        @Override public List<NotificationOperationsPolicyVersion> findPolicyVersions(String environment) { return List.copyOf(policies.values()); }
        @Override public NotificationOperationsPolicyVersion publishPolicyVersion(long id, String actor, Instant at) {
            var current = policies.get(id);
            published = new NotificationOperationsPolicyVersion(current.id(), current.environmentCode(),
                    current.versionNo(), current.minimumOperationalAttempts(), current.warningMinimumSuccessRate(),
                    current.criticalMinimumSuccessRate(), current.criticalEscalationAfter(),
                    current.repeatNotificationAfter(), "PUBLISHED", current.contentChecksum(), current.createdBy(),
                    actor, at, current.createdAt());
            policies.put(id, published); return published;
        }
        @Override public NotificationMaintenanceWindow createMaintenanceWindow(NotificationMaintenanceWindow value) {
            long id = windows.size() + 1L;
            var saved = new NotificationMaintenanceWindow(id, value.environmentCode(), value.windowStart(),
                    value.windowEnd(), value.reason(), "SCHEDULED", value.createdBy(), null, null, NOW, NOW);
            windows.put(id, saved); overlapping = saved; return saved;
        }
        @Override public Optional<NotificationMaintenanceWindow> findMaintenanceWindow(long id) { return Optional.ofNullable(windows.get(id)); }
        @Override public Optional<NotificationMaintenanceWindow> findActiveMaintenanceWindow(String environment, Instant at) { return Optional.empty(); }
        @Override public Optional<NotificationMaintenanceWindow> findOverlappingMaintenanceWindow(String environment, Instant start, Instant end) { return Optional.ofNullable(overlapping); }
        @Override public boolean hasOverlappingMaintenanceWindow(String environment, Instant start, Instant end) {
            return windows.values().stream().anyMatch(v -> "SCHEDULED".equals(v.status())
                    && v.environmentCode().equals(environment) && v.windowStart().isBefore(end)
                    && v.windowEnd().isAfter(start));
        }
        @Override public List<NotificationMaintenanceWindow> findMaintenanceWindows(String environment, int limit) { return List.copyOf(windows.values()); }
        @Override public NotificationMaintenanceWindow cancelMaintenanceWindow(long id, String actor, Instant at) {
            var current = windows.get(id);
            var saved = new NotificationMaintenanceWindow(id, current.environmentCode(), current.windowStart(),
                    current.windowEnd(), current.reason(), "CANCELLED", current.createdBy(), actor, at,
                    current.createdAt(), at);
            windows.put(id, saved); overlapping = null; return saved;
        }
        private static UnsupportedOperationException unsupported() { return new UnsupportedOperationException(); }
    }
}
