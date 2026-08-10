package com.ftk.tpip.control.application.deployment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.DeploymentRuntimeProperties;
import com.ftk.tpip.release.domain.model.DeploymentHealthAction;
import com.ftk.tpip.release.domain.model.DeploymentHealthDecision;
import com.ftk.tpip.release.domain.model.DeploymentHealthEvaluation;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlert;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlertStatus;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.DeploymentStatus;
import com.ftk.tpip.release.domain.model.IntegrationDeployment;
import com.ftk.tpip.release.domain.model.NotificationDeliveryStatus;
import com.ftk.tpip.release.domain.model.NotificationDeliveryTask;
import com.ftk.tpip.release.domain.repository.DeploymentHealthEvaluationRepository;
import com.ftk.tpip.release.domain.repository.DeploymentHealthAlertRepository;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import com.ftk.tpip.shared.AssetCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DeploymentHealthApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void automaticallyRollsBackUnhealthyCanaryAndPersistsThresholdSnapshot() {
        FakeDeployments deployments = new FakeDeployments(activeCanary());
        FakeEvaluations evaluations = new FakeEvaluations();
        var service = service(deployments, evaluations);

        DeploymentHealthEvaluation result = service.evaluate(2, NOW.minusSeconds(300), NOW,
                100, 8, 900, json.createObjectNode().put("source", "prometheus"), "health-worker");

        assertEquals(DeploymentHealthDecision.UNHEALTHY, result.decision());
        assertEquals(DeploymentHealthAction.AUTO_ROLLBACK, result.action());
        assertEquals(3L, result.rollbackDeploymentId());
        assertEquals(1, deployments.rollbackCalls);
        assertEquals("8.0000", result.errorRatePercentage().toPlainString());
    }

    @Test
    void insufficientSamplesDoNotMutateDeployment() {
        FakeDeployments deployments = new FakeDeployments(activeCanary());
        var service = service(deployments, new FakeEvaluations());
        DeploymentHealthEvaluation result = service.evaluate(2, NOW.minusSeconds(60), NOW,
                9, 9, 9000, null, "health-worker");
        assertEquals(DeploymentHealthDecision.INSUFFICIENT_DATA, result.decision());
        assertEquals(DeploymentHealthAction.NONE, result.action());
        assertNull(result.rollbackDeploymentId());
        assertEquals(0, deployments.rollbackCalls);
    }

    @Test
    void returnsExistingEvaluationForAnAlreadyProcessedWindow() {
        FakeDeployments deployments = new FakeDeployments(activeCanary());
        FakeEvaluations evaluations = new FakeEvaluations();
        var service = service(deployments, evaluations);
        DeploymentHealthEvaluation first = service.evaluate(2, NOW.minusSeconds(300), NOW,
                100, 0, 100, null, "health-worker");
        DeploymentHealthEvaluation repeated = service.evaluate(2, NOW.minusSeconds(300), NOW,
                100, 99, 9999, null, "health-worker");
        assertEquals(first, repeated);
        assertEquals(1, evaluations.values.size());
        assertEquals(0, deployments.rollbackCalls);
    }

    @Test
    void requiresTwoAdjacentUnhealthyWindowsBeforeRollback() {
        FakeDeployments deployments = new FakeDeployments(activeCanary());
        FakeEvaluations evaluations = new FakeEvaluations();
        FakeAlerts alerts = new FakeAlerts();
        FakeOutbox outbox = new FakeOutbox();
        var properties = properties(2);
        var service = service(deployments, evaluations, alerts, outbox, properties);

        DeploymentHealthEvaluation first = service.evaluate(2, NOW.minusSeconds(600), NOW.minusSeconds(300),
                100, 8, 900, null, "health-worker");
        DeploymentHealthEvaluation second = service.evaluate(2, NOW.minusSeconds(300), NOW,
                100, 7, 800, null, "health-worker");

        assertEquals(DeploymentHealthAction.NONE, first.action());
        assertEquals(DeploymentHealthAction.AUTO_ROLLBACK, second.action());
        assertEquals(1, deployments.rollbackCalls);
        assertEquals(2, alerts.values.size());
        assertEquals("TPIP_CANARY_UNHEALTHY_WINDOW", alerts.values.get(0).alertCode());
        assertEquals(DeploymentHealthAlertStatus.RESOLVED, alerts.values.get(0).status());
        assertEquals("TPIP_CANARY_AUTO_ROLLBACK", alerts.values.get(1).alertCode());
        assertEquals(List.of("TPIP_HEALTH_ALERT_OPENED", "TPIP_HEALTH_ALERT_RESOLVED",
                        "TPIP_HEALTH_ALERT_OPENED"),
                outbox.values.stream().map(NotificationOutboxMessage::eventType).toList());
    }

    @Test
    void criticalThresholdBypassesConsecutiveWindowRequirement() {
        FakeDeployments deployments = new FakeDeployments(activeCanary());
        var service = service(deployments, new FakeEvaluations(), new FakeAlerts(), properties(3));
        DeploymentHealthEvaluation result = service.evaluate(2, NOW.minusSeconds(300), NOW,
                100, 25, 900, null, "health-worker");
        assertEquals(DeploymentHealthAction.AUTO_ROLLBACK, result.action());
        assertEquals(1, deployments.rollbackCalls);
    }

    @Test
    void healthyWindowResolvesOpenAlertAndWritesNotificationEvents() {
        FakeDeployments deployments = new FakeDeployments(activeCanary());
        FakeAlerts alerts = new FakeAlerts();
        FakeOutbox outbox = new FakeOutbox();
        var service = service(deployments, new FakeEvaluations(), alerts, outbox, properties(3));
        service.evaluate(2, NOW.minusSeconds(600), NOW.minusSeconds(300),
                100, 8, 900, null, "health-worker");
        service.evaluate(2, NOW.minusSeconds(300), NOW,
                100, 0, 100, null, "health-worker");
        assertEquals(DeploymentHealthAlertStatus.RESOLVED, alerts.values.getFirst().status());
        assertEquals(List.of("TPIP_HEALTH_ALERT_OPENED", "TPIP_HEALTH_ALERT_RESOLVED"),
                outbox.values.stream().map(NotificationOutboxMessage::eventType).toList());
    }

    private DeploymentHealthApplicationService service(FakeDeployments deployments, FakeEvaluations evaluations) {
        return service(deployments, evaluations, new FakeAlerts(), properties(1));
    }

    private DeploymentRuntimeProperties properties(int consecutiveWindows) {
        var properties = new DeploymentRuntimeProperties();
        properties.setHealthMinimumSamples(10);
        properties.setHealthMaximumErrorRate(new BigDecimal("5.00"));
        properties.setHealthMaximumP95LatencyMs(2000);
        properties.setHealthAutoRollbackEnabled(true);
        properties.setHealthConsecutiveUnhealthyWindows(consecutiveWindows);
        properties.setHealthCriticalErrorRate(new BigDecimal("20.00"));
        properties.setHealthCriticalP95LatencyMs(5000);
        return properties;
    }

    private DeploymentHealthApplicationService service(FakeDeployments deployments, FakeEvaluations evaluations,
            FakeAlerts alerts, DeploymentRuntimeProperties properties) {
        return service(deployments, evaluations, alerts, new FakeOutbox(), properties);
    }

    private DeploymentHealthApplicationService service(FakeDeployments deployments, FakeEvaluations evaluations,
            FakeAlerts alerts, FakeOutbox outbox, DeploymentRuntimeProperties properties) {
        return new DeploymentHealthApplicationService(deployments, evaluations, alerts, outbox,
                new CanonicalJsonService(json), json, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static IntegrationDeployment activeCanary() {
        return new IntegrationDeployment(2L, AssetCode.of("customer.lookup.canary"), 2, 1, "test",
                DeploymentStatus.ACTIVE, new BigDecimal("10.00"), 1L, "{}", "{}", "{}", 3,
                "operator", NOW.minusSeconds(600), NOW.minusSeconds(600), null, NOW.minusSeconds(600));
    }

    private static final class FakeDeployments extends DeploymentApplicationService {
        private final IntegrationDeployment target;
        private int rollbackCalls;

        private FakeDeployments(IntegrationDeployment target) {
            super(null, null, null, null, null, null, Clock.fixed(NOW, ZoneOffset.UTC));
            this.target = target;
        }

        @Override public IntegrationDeployment get(long id) { return target; }

        @Override public IntegrationDeployment rollback(long id, long rowVersion, String code, String reason, String actor) {
            rollbackCalls++;
            return new IntegrationDeployment(3L, AssetCode.of(code), 1, 1, "test", DeploymentStatus.ACTIVE,
                    new BigDecimal("100.00"), 2L, "{}", "{}", "{}", 0, actor, NOW, NOW, null, NOW);
        }
    }

    private static final class FakeEvaluations implements DeploymentHealthEvaluationRepository {
        private final List<DeploymentHealthEvaluation> values = new ArrayList<>();
        @Override public DeploymentHealthEvaluation create(DeploymentHealthEvaluation value) {
            DeploymentHealthEvaluation saved = new DeploymentHealthEvaluation((long) values.size() + 1, value.deploymentId(),
                    value.windowStart(), value.windowEnd(), value.sampleCount(), value.failureCount(),
                    value.errorRatePercentage(), value.p95LatencyMs(), value.decision(), value.action(),
                    value.rollbackDeploymentId(), value.evidence(), value.evaluatedBy(), NOW);
            values.add(saved);
            return saved;
        }
        @Override public List<DeploymentHealthEvaluation> findByDeploymentId(long deploymentId) {
            return List.copyOf(values);
        }
        @Override public Optional<DeploymentHealthEvaluation> findByDeploymentAndWindow(long deploymentId,
                Instant windowStart, Instant windowEnd) {
            return values.stream().filter(value -> value.deploymentId() == deploymentId
                    && value.windowStart().equals(windowStart) && value.windowEnd().equals(windowEnd)).findFirst();
        }
        @Override public List<DeploymentHealthEvaluation> findLatestBefore(long deploymentId,
                Instant windowStart, int limit) {
            return values.stream().filter(value -> value.deploymentId() == deploymentId
                    && !value.windowEnd().isAfter(windowStart))
                    .sorted((left, right) -> right.windowEnd().compareTo(left.windowEnd())).limit(limit).toList();
        }
    }

    private static final class FakeAlerts implements DeploymentHealthAlertRepository {
        private final List<DeploymentHealthAlert> values = new ArrayList<>();
        @Override public DeploymentHealthAlert create(DeploymentHealthAlert value) {
            DeploymentHealthAlert saved = new DeploymentHealthAlert((long) values.size() + 1,
                    value.deploymentId(), value.evaluationId(), value.alertCode(), value.severity(), value.status(),
                    value.summary(), value.details(), null, null, null, NOW, NOW);
            values.add(saved);
            return saved;
        }
        @Override public Optional<DeploymentHealthAlert> findById(long id) {
            return values.stream().filter(value -> value.id() == id).findFirst();
        }
        @Override public List<DeploymentHealthAlert> findByDeploymentId(long deploymentId) {
            return values.stream().filter(value -> value.deploymentId() == deploymentId).toList();
        }
        @Override public DeploymentHealthAlert acknowledge(long id, String actor, Instant at) {
            DeploymentHealthAlert value = findById(id).orElseThrow();
            DeploymentHealthAlert updated = new DeploymentHealthAlert(value.id(), value.deploymentId(), value.evaluationId(),
                    value.alertCode(), value.severity(), DeploymentHealthAlertStatus.ACKNOWLEDGED, value.summary(),
                    value.details(), actor, at, null, value.createdAt(), at);
            values.set(values.indexOf(value), updated);
            return updated;
        }
        @Override public List<DeploymentHealthAlert> resolveOpen(long deploymentId, Instant at) {
            List<DeploymentHealthAlert> resolved = new ArrayList<>();
            for (int index = 0; index < values.size(); index++) {
                DeploymentHealthAlert value = values.get(index);
                if (value.deploymentId() != deploymentId || value.status() == DeploymentHealthAlertStatus.RESOLVED) continue;
                DeploymentHealthAlert updated = new DeploymentHealthAlert(value.id(), value.deploymentId(),
                        value.evaluationId(), value.alertCode(), value.severity(), DeploymentHealthAlertStatus.RESOLVED,
                        value.summary(), value.details(), value.acknowledgedBy(), value.acknowledgedAt(), at,
                        value.createdAt(), at);
                values.set(index, updated);
                resolved.add(updated);
            }
            return resolved;
        }
    }

    private static final class FakeOutbox implements NotificationOutboxRepository {
        private final List<NotificationOutboxMessage> values = new ArrayList<>();
        @Override public NotificationOutboxMessage enqueue(NotificationOutboxMessage value) {
            NotificationOutboxMessage saved = new NotificationOutboxMessage((long) values.size() + 1,
                    value.eventType(), value.aggregateType(),
                    value.aggregateId(), value.environmentCode(), value.payload(), value.availableAt(), NOW);
            values.add(saved);
            return saved;
        }
        @Override public List<NotificationDeliveryTask> claim(String workerId, Instant now,
                Instant expiredBefore, int batchSize) {
            throw new UnsupportedOperationException();
        }
        @Override public Optional<NotificationDeliveryTask> findById(long id) {
            return Optional.empty();
        }
        @Override public NotificationDeliveryTask markDelivered(long id, String workerId, Instant deliveredAt) {
            throw new UnsupportedOperationException();
        }
        @Override public NotificationDeliveryTask markFailed(long id, String workerId,
                Instant availableAt, String error,
                com.ftk.tpip.release.domain.model.NotificationFailureClass failureClass,
                long retryDelayMillis, boolean deadLetter, Instant failedAt) {
            throw new UnsupportedOperationException();
        }
        @Override public List<NotificationDeliveryTask> findByStatus(NotificationDeliveryStatus status, int limit) {
            throw new UnsupportedOperationException();
        }
        @Override public NotificationDeliveryTask replay(long id, Instant availableAt, String actor) {
            throw new UnsupportedOperationException();
        }
        @Override public List<com.ftk.tpip.release.domain.model.NotificationRoutingFailure> findRoutingFailures(int limit) { return List.of(); }
        @Override public com.ftk.tpip.release.domain.model.NotificationRoutingFailure reroute(long id,String actor) { throw new UnsupportedOperationException(); }
        @Override public long countRoutingFailures() { return 0; }
        @Override public List<NotificationDeliveryTask> replayBatch(List<Long> ids, Instant at, String actor) {
            throw new UnsupportedOperationException();
        }
        @Override public List<com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptAggregate> aggregateAttempts(
                Instant from, Instant to, String environment, String channel,
                com.ftk.tpip.release.domain.model.NotificationProviderType provider, Long endpoint) {
            return List.of();
        }
    }
}
