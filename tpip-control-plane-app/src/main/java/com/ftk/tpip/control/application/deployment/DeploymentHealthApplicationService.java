package com.ftk.tpip.control.application.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.DeploymentRuntimeProperties;
import com.ftk.tpip.release.domain.model.DeploymentHealthAction;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlert;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlertSeverity;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlertStatus;
import com.ftk.tpip.release.domain.model.DeploymentHealthDecision;
import com.ftk.tpip.release.domain.model.DeploymentHealthEvaluation;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.model.DeploymentStatus;
import com.ftk.tpip.release.domain.model.IntegrationDeployment;
import com.ftk.tpip.release.domain.repository.DeploymentHealthEvaluationRepository;
import com.ftk.tpip.release.domain.repository.DeploymentHealthAlertRepository;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentHealthApplicationService {
    private final DeploymentApplicationService deployments;
    private final DeploymentHealthEvaluationRepository evaluations;
    private final DeploymentHealthAlertRepository alerts;
    private final NotificationOutboxRepository outbox;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final DeploymentRuntimeProperties properties;
    private final Clock clock;

    @Autowired
    public DeploymentHealthApplicationService(DeploymentApplicationService deployments,
            DeploymentHealthEvaluationRepository evaluations, DeploymentHealthAlertRepository alerts,
            NotificationOutboxRepository outbox, CanonicalJsonService canonicalJson,
            ObjectMapper json, DeploymentRuntimeProperties properties) {
        this(deployments, evaluations, alerts, outbox, canonicalJson, json, properties, Clock.systemUTC());
    }

    DeploymentHealthApplicationService(DeploymentApplicationService deployments,
            DeploymentHealthEvaluationRepository evaluations, DeploymentHealthAlertRepository alerts,
            NotificationOutboxRepository outbox, CanonicalJsonService canonicalJson,
            ObjectMapper json, DeploymentRuntimeProperties properties, Clock clock) {
        this.deployments = deployments;
        this.evaluations = evaluations;
        this.alerts = alerts;
        this.outbox = outbox;
        this.canonicalJson = canonicalJson;
        this.json = json;
        this.properties = properties;
        this.clock = clock;
        validateProperties();
    }

    @Transactional
    public DeploymentHealthEvaluation evaluate(long deploymentId, Instant windowStart, Instant windowEnd,
            long sampleCount, long failureCount, long p95LatencyMs, JsonNode evidence, String actor) {
        validateWindow(windowStart, windowEnd);
        var existing = evaluations.findByDeploymentAndWindow(deploymentId, windowStart, windowEnd);
        if (existing.isPresent()) return existing.get();
        IntegrationDeployment target = deployments.get(deploymentId);
        if (target.deploymentStatus() != DeploymentStatus.ACTIVE || target.previousDeploymentId() == null) {
            throw new IllegalArgumentException("health evaluation requires an ACTIVE canary deployment");
        }
        if (sampleCount < 0 || failureCount < 0 || failureCount > sampleCount || p95LatencyMs < 0) {
            throw new IllegalArgumentException("health metrics are invalid");
        }
        String evaluator = actor(actor);
        BigDecimal errorRate = sampleCount == 0 ? BigDecimal.ZERO.setScale(4)
                : BigDecimal.valueOf(failureCount).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(sampleCount), 4, RoundingMode.HALF_UP);
        DeploymentHealthDecision decision = decision(sampleCount, errorRate, p95LatencyMs);
        int unhealthyStreak = decision == DeploymentHealthDecision.UNHEALTHY
                ? unhealthyStreak(deploymentId, windowStart) : 0;
        boolean critical = decision == DeploymentHealthDecision.UNHEALTHY
                && (errorRate.compareTo(properties.getHealthCriticalErrorRate()) >= 0
                || p95LatencyMs >= properties.getHealthCriticalP95LatencyMs());
        DeploymentHealthAction action = DeploymentHealthAction.NONE;
        Long rollbackDeploymentId = null;
        boolean protectionTriggered = critical
                || unhealthyStreak >= properties.getHealthConsecutiveUnhealthyWindows();
        if (decision == DeploymentHealthDecision.UNHEALTHY && protectionTriggered
                && properties.isHealthAutoRollbackEnabled()) {
            IntegrationDeployment rollback = deployments.rollback(target.id(), target.rowVersion(),
                    rollbackCode(target, clock.instant()), "Automatic health protection: errorRate=" + errorRate
                            + "%, p95LatencyMs=" + p95LatencyMs, "system-health-gate");
            action = DeploymentHealthAction.AUTO_ROLLBACK;
            rollbackDeploymentId = rollback.id();
        }
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("minimumSamples", properties.getHealthMinimumSamples());
        snapshot.put("maximumErrorRate", properties.getHealthMaximumErrorRate());
        snapshot.put("maximumP95LatencyMs", properties.getHealthMaximumP95LatencyMs());
        snapshot.put("autoRollbackEnabled", properties.isHealthAutoRollbackEnabled());
        snapshot.put("consecutiveUnhealthyWindowsRequired", properties.getHealthConsecutiveUnhealthyWindows());
        snapshot.put("consecutiveUnhealthyWindowsObserved", unhealthyStreak);
        snapshot.put("criticalErrorRate", properties.getHealthCriticalErrorRate());
        snapshot.put("criticalP95LatencyMs", properties.getHealthCriticalP95LatencyMs());
        snapshot.put("criticalThresholdBreached", critical);
        snapshot.put("protectionTriggered", protectionTriggered);
        if (evidence != null && !evidence.isNull()) {
            if (!evidence.isObject()) throw new IllegalArgumentException("evidence must be an object");
            snapshot.set("sourceEvidence", evidence);
        }
        DeploymentHealthEvaluation saved = evaluations.create(new DeploymentHealthEvaluation(null, deploymentId, windowStart, windowEnd,
                sampleCount, failureCount, errorRate, p95LatencyMs, decision, action, rollbackDeploymentId,
                canonicalJson.canonicalString(snapshot), evaluator, null));
        if (decision == DeploymentHealthDecision.UNHEALTHY) {
            createAlert(target, saved, unhealthyStreak, critical);
        } else if (decision == DeploymentHealthDecision.HEALTHY) {
            resolveAlerts(target, saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public List<DeploymentHealthEvaluation> list(long deploymentId) {
        deployments.get(deploymentId);
        return evaluations.findByDeploymentId(deploymentId);
    }

    @Transactional(readOnly = true)
    public List<DeploymentHealthAlert> listAlerts(long deploymentId) {
        deployments.get(deploymentId);
        return alerts.findByDeploymentId(deploymentId);
    }

    @Transactional
    public DeploymentHealthAlert acknowledgeAlert(long deploymentId, long alertId, String actor) {
        IntegrationDeployment deployment = deployments.get(deploymentId);
        DeploymentHealthAlert current = alerts.findById(alertId)
                .orElseThrow(() -> new DeploymentHealthAlertNotFoundException(alertId));
        if (current.deploymentId() != deploymentId) throw new DeploymentHealthAlertNotFoundException(alertId);
        DeploymentHealthAlert acknowledged = alerts.acknowledge(alertId, actor(actor), clock.instant());
        enqueue("TPIP_HEALTH_ALERT_ACKNOWLEDGED", acknowledged, deployment.environmentCode(), json.createObjectNode()
                .put("acknowledgedBy", acknowledged.acknowledgedBy()));
        return acknowledged;
    }

    private int unhealthyStreak(long deploymentId, Instant currentWindowStart) {
        int streak = 1;
        Instant expectedEnd = currentWindowStart;
        int previousNeeded = properties.getHealthConsecutiveUnhealthyWindows() - 1;
        for (DeploymentHealthEvaluation previous : evaluations.findLatestBefore(
                deploymentId, currentWindowStart, Math.max(previousNeeded, 1))) {
            if (!previous.windowEnd().equals(expectedEnd)
                    || previous.decision() != DeploymentHealthDecision.UNHEALTHY) break;
            streak++;
            expectedEnd = previous.windowStart();
            if (streak >= properties.getHealthConsecutiveUnhealthyWindows()) break;
        }
        return streak;
    }

    private void createAlert(IntegrationDeployment deployment, DeploymentHealthEvaluation evaluation,
            int streak, boolean critical) {
        boolean protectedNow = evaluation.action() == DeploymentHealthAction.AUTO_ROLLBACK;
        if (protectedNow) resolvePriorAlerts(deployment, evaluation, "ESCALATED_TO_CRITICAL");
        DeploymentHealthAlertSeverity severity = protectedNow || critical
                ? DeploymentHealthAlertSeverity.CRITICAL : DeploymentHealthAlertSeverity.WARNING;
        String code = protectedNow ? "TPIP_CANARY_AUTO_ROLLBACK" : "TPIP_CANARY_UNHEALTHY_WINDOW";
        ObjectNode details = json.createObjectNode();
        details.put("deploymentCode", deployment.deploymentCode().value());
        details.put("errorRatePercentage", evaluation.errorRatePercentage());
        details.put("p95LatencyMs", evaluation.p95LatencyMs());
        details.put("unhealthyStreak", streak);
        details.put("criticalThresholdBreached", critical);
        details.put("action", evaluation.action().name());
        if (evaluation.rollbackDeploymentId() != null) details.put("rollbackDeploymentId", evaluation.rollbackDeploymentId());
        String summary = protectedNow
                ? "Canary was automatically rolled back by health protection"
                : "Canary health window is unhealthy; awaiting consecutive-window threshold";
        DeploymentHealthAlert alert = alerts.create(new DeploymentHealthAlert(null, deployment.id(),
                evaluation.id(), code, severity, DeploymentHealthAlertStatus.OPEN, summary,
                canonicalJson.canonicalString(details), null, null, null, null, null));
        enqueue("TPIP_HEALTH_ALERT_OPENED", alert, deployment.environmentCode(), details);
    }

    private void resolveAlerts(IntegrationDeployment deployment, DeploymentHealthEvaluation evaluation) {
        resolvePriorAlerts(deployment, evaluation, "HEALTH_RECOVERED");
    }

    private void resolvePriorAlerts(IntegrationDeployment deployment, DeploymentHealthEvaluation evaluation,
            String reason) {
        for (DeploymentHealthAlert resolved : alerts.resolveOpen(deployment.id(), clock.instant())) {
            ObjectNode details = json.createObjectNode();
            details.put("deploymentCode", deployment.deploymentCode().value());
            details.put("recoveryEvaluationId", evaluation.id());
            details.put("reason", reason);
            enqueue("TPIP_HEALTH_ALERT_RESOLVED", resolved, deployment.environmentCode(), details);
        }
    }

    private void enqueue(String eventType, DeploymentHealthAlert alert, String environmentCode,
            ObjectNode eventDetails) {
        ObjectNode payload = json.createObjectNode();
        payload.put("alertId", alert.id());
        payload.put("deploymentId", alert.deploymentId());
        payload.put("evaluationId", alert.evaluationId());
        payload.put("alertCode", alert.alertCode());
        payload.put("severity", alert.severity().name());
        payload.put("status", alert.status().name());
        payload.set("details", eventDetails);
        outbox.enqueue(new NotificationOutboxMessage(null, eventType, "DEPLOYMENT_HEALTH_ALERT",
                Long.toString(alert.id()), environmentCode, canonicalJson.canonicalString(payload),
                clock.instant(), null));
    }

    private DeploymentHealthDecision decision(long samples, BigDecimal errorRate, long p95LatencyMs) {
        if (samples < properties.getHealthMinimumSamples()) return DeploymentHealthDecision.INSUFFICIENT_DATA;
        if (errorRate.compareTo(properties.getHealthMaximumErrorRate()) > 0
                || p95LatencyMs > properties.getHealthMaximumP95LatencyMs()) {
            return DeploymentHealthDecision.UNHEALTHY;
        }
        return DeploymentHealthDecision.HEALTHY;
    }

    private void validateWindow(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("health window is invalid");
        }
        if (end.isAfter(clock.instant().plusSeconds(60))) {
            throw new IllegalArgumentException("health window must not be in the future");
        }
    }

    private void validateProperties() {
        if (properties.getHealthMinimumSamples() < 1
                || properties.getHealthMaximumErrorRate() == null
                || properties.getHealthMaximumErrorRate().signum() < 0
                || properties.getHealthMaximumErrorRate().compareTo(new BigDecimal("100")) > 0
                || properties.getHealthMaximumP95LatencyMs() < 1) {
            throw new IllegalArgumentException("deployment health thresholds are invalid");
        }
        if (properties.getHealthConsecutiveUnhealthyWindows() < 1
                || properties.getHealthCriticalErrorRate() == null
                || properties.getHealthCriticalErrorRate().compareTo(properties.getHealthMaximumErrorRate()) < 0
                || properties.getHealthCriticalErrorRate().compareTo(new BigDecimal("100")) > 0
                || properties.getHealthCriticalP95LatencyMs() < properties.getHealthMaximumP95LatencyMs()) {
            throw new IllegalArgumentException("deployment critical health thresholds are invalid");
        }
    }

    private static String rollbackCode(IntegrationDeployment deployment, Instant now) {
        String suffix = ".auto-rollback-" + now.getEpochSecond();
        String base = deployment.deploymentCode().value();
        if (base.length() + suffix.length() > 200) base = base.substring(0, 200 - suffix.length());
        return base + suffix;
    }

    private static String actor(String value) {
        if (value == null || value.isBlank() || value.trim().length() > 100) {
            throw new IllegalArgumentException("X-Operator is blank or too long");
        }
        return value.trim();
    }
}
