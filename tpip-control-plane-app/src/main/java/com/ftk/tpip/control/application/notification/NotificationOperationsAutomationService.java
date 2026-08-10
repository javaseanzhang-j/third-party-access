package com.ftk.tpip.control.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationOperationsAlert;
import com.ftk.tpip.release.domain.model.NotificationOperationsEvaluation;
import com.ftk.tpip.release.domain.model.NotificationOutboxMessage;
import com.ftk.tpip.release.domain.repository.NotificationOperationsRepository;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class NotificationOperationsAutomationService {
    private static final String AGGREGATE_TYPE = "NOTIFICATION_OPERATIONS_ALERT";
    private final NotificationOperationsApplicationService summaries;
    private final NotificationOperationsRepository operations;
    private final NotificationOutboxRepository outbox;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final NotificationDeliveryProperties properties;
    private final NotificationOperationsGovernanceService governance;
    private final Clock clock;

    @Autowired
    public NotificationOperationsAutomationService(NotificationOperationsApplicationService summaries,
            NotificationOperationsRepository operations, NotificationOutboxRepository outbox,
            CanonicalJsonService canonicalJson, ObjectMapper json, NotificationDeliveryProperties properties,
            NotificationOperationsGovernanceService governance) {
        this(summaries, operations, outbox, canonicalJson, json, properties, governance, Clock.systemUTC());
    }

    NotificationOperationsAutomationService(NotificationOperationsApplicationService summaries,
            NotificationOperationsRepository operations, NotificationOutboxRepository outbox,
            CanonicalJsonService canonicalJson, ObjectMapper json, NotificationDeliveryProperties properties,
            NotificationOperationsGovernanceService governance, Clock clock) {
        this.summaries = summaries;
        this.operations = operations;
        this.outbox = outbox;
        this.canonicalJson = canonicalJson;
        this.json = json;
        this.properties = properties;
        this.governance = governance;
        this.clock = clock;
        properties.validate();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NotificationOperationsEvaluation evaluate(String environmentCode, Instant windowStart,
            Instant windowEnd, String actor) {
        String environment = requiredEnvironment(environmentCode);
        validateWindow(windowStart, windowEnd);
        var existing = operations.findEvaluation(environment, windowStart, windowEnd);
        if (existing.isPresent()) return existing.get();
        NotificationOperationsSummary summary = summaries.summary(windowStart, windowEnd,
                environment, null, null, null);
        var policy = governance.effectivePolicy(environment);
        var maintenance = governance.overlappingMaintenance(environment, windowStart, windowEnd);
        String health = health(summary, policy);
        Instant now = clock.instant();
        NotificationOperationsEvaluation draft = new NotificationOperationsEvaluation(null, environment,
                windowStart, windowEnd, summary.attemptCount(), summary.successCount(), summary.failureCount(),
                summary.deadLetterCount(), summary.successRate(), health,
                thresholdSnapshot(policy, maintenance.map(value -> value.id()).orElse(null)),
                requiredActor(actor), now);
        if (!operations.createEvaluationIfAbsent(draft)) {
            return operations.findEvaluation(environment, windowStart, windowEnd).orElseThrow();
        }
        NotificationOperationsEvaluation saved = operations.findEvaluation(environment, windowStart, windowEnd)
                .orElseThrow();
        if (maintenance.isEmpty() || "HEALTHY".equals(saved.healthStatus())) applyAlertLifecycle(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<NotificationOperationsEvaluation> listEvaluations(String environmentCode, int limit) {
        return operations.findEvaluations(requiredEnvironment(environmentCode), limit(limit));
    }

    @Transactional(readOnly = true)
    public List<NotificationOperationsAlert> listAlerts(String environmentCode, int limit) {
        return operations.findAlerts(requiredEnvironment(environmentCode), limit(limit));
    }

    @Transactional
    public NotificationOperationsAlert acknowledgeAlert(String environmentCode, long alertId, String actor) {
        String environment = requiredEnvironment(environmentCode);
        if (alertId <= 0) throw new IllegalArgumentException("alertId must be positive");
        NotificationOperationsAlert current = operations.findAlert(alertId)
                .orElseThrow(() -> new IllegalArgumentException("notification operations alert does not exist"));
        if (!environment.equals(current.environmentCode())) {
            throw new IllegalArgumentException("notification operations alert does not belong to environment");
        }
        NotificationOperationsAlert acknowledged = operations.acknowledgeAlert(alertId, requiredActor(actor),
                clock.instant());
        enqueue("TPIP_NOTIFICATION_OPERATIONS_ALERT_ACKNOWLEDGED", acknowledged,
                details(acknowledged).put("acknowledgedBy", acknowledged.acknowledgedBy()));
        return acknowledged;
    }

    private void applyAlertLifecycle(NotificationOperationsEvaluation evaluation) {
        switch (evaluation.healthStatus()) {
            case "HEALTHY" -> resolve(evaluation, "HEALTH_RECOVERED");
            case "WARNING", "CRITICAL" -> openOrEscalate(evaluation);
            case "INSUFFICIENT_DATA" -> { }
            default -> throw new IllegalStateException("unsupported operations health status");
        }
    }

    private void openOrEscalate(NotificationOperationsEvaluation evaluation) {
        var active = operations.findActiveAlert(evaluation.environmentCode());
        if (active.isPresent()) {
            if (active.get().severity().equals(evaluation.healthStatus())
                    || "CRITICAL".equals(active.get().severity())) return;
            resolve(evaluation, "ESCALATED_TO_CRITICAL");
        }
        String severity = evaluation.healthStatus();
        String code = "TPIP_NOTIFICATION_OPERATIONS_" + severity;
        String summary = "CRITICAL".equals(severity)
                ? "Notification delivery success rate breached the critical threshold"
                : "Notification delivery health requires attention";
        NotificationOperationsAlert alert = operations.createAlert(new NotificationOperationsAlert(null,
                evaluation.environmentCode(), evaluation.id(), code, severity, "OPEN", summary,
                canonicalJson.canonicalString(evaluationDetails(evaluation)), null, null, null, null, null, null, null));
        enqueue("TPIP_NOTIFICATION_OPERATIONS_ALERT_OPENED", alert, details(alert));
    }

    private void resolve(NotificationOperationsEvaluation evaluation, String reason) {
        for (NotificationOperationsAlert alert : operations.resolveActiveAlerts(
                evaluation.environmentCode(), clock.instant())) {
            ObjectNode event = details(alert);
            event.put("recoveryEvaluationId", evaluation.id()).put("reason", reason);
            enqueue("TPIP_NOTIFICATION_OPERATIONS_ALERT_RESOLVED", alert, event);
        }
    }

    private void enqueue(String eventType, NotificationOperationsAlert alert, ObjectNode eventDetails) {
        ObjectNode payload = details(alert);
        payload.set("details", eventDetails);
        outbox.enqueue(new NotificationOutboxMessage(null, eventType, AGGREGATE_TYPE,
                Long.toString(alert.id()), alert.environmentCode(), canonicalJson.canonicalString(payload),
                clock.instant(), null));
    }

    @Transactional
    public int escalateDueAlerts(String environmentCode) {
        String environment = requiredEnvironment(environmentCode);
        var policy = governance.effectivePolicy(environment);
        if (policy.criticalEscalationAfter().isZero()) return 0;
        Instant now = clock.instant();
        if (governance.overlappingMaintenance(environment, now, now.plusMillis(1)).isPresent()) return 0;
        int escalated = 0;
        for (NotificationOperationsAlert alert : operations.findAlerts(environment, 500)) {
            if (!"OPEN".equals(alert.status()) || !"CRITICAL".equals(alert.severity())
                    || alert.escalatedAt() != null || alert.createdAt() == null
                    || alert.createdAt().plus(policy.criticalEscalationAfter()).isAfter(now)) continue;
            var saved = operations.markEscalated(alert.id(), now);
            if (saved.isPresent()) {
                enqueue("TPIP_NOTIFICATION_OPERATIONS_ALERT_ESCALATED", saved.get(), details(saved.get())
                        .put("policyVersionNo", policy.versionNo())
                        .put("criticalEscalationAfter", policy.criticalEscalationAfter().toString()));
                escalated++;
            }
        }
        return escalated;
    }

    @Transactional
    public int repeatDueAlerts(String environmentCode) {
        String environment = requiredEnvironment(environmentCode);
        var policy = governance.effectivePolicy(environment);
        if (policy.repeatNotificationAfter().isZero()) return 0;
        Instant now = clock.instant();
        if (governance.overlappingMaintenance(environment, now, now.plusMillis(1)).isPresent()) return 0;
        Instant eligibleBefore = now.minus(policy.repeatNotificationAfter());
        int repeated = 0;
        for (NotificationOperationsAlert alert : operations.findAlerts(environment, 500)) {
            if (!"OPEN".equals(alert.status())) continue;
            var saved = operations.markRepeatNotified(alert.id(), eligibleBefore, now);
            if (saved.isPresent()) {
                enqueue("TPIP_NOTIFICATION_OPERATIONS_ALERT_REPEATED", saved.get(), details(saved.get())
                        .put("policyVersionNo", policy.versionNo())
                        .put("repeatNotificationAfter", policy.repeatNotificationAfter().toString()));
                repeated++;
            }
        }
        return repeated;
    }

    private ObjectNode details(NotificationOperationsAlert alert) {
        return json.createObjectNode().put("alertId", alert.id()).put("evaluationId", alert.evaluationId())
                .put("alertCode", alert.alertCode()).put("severity", alert.severity()).put("status", alert.status());
    }

    private ObjectNode evaluationDetails(NotificationOperationsEvaluation evaluation) {
        return json.createObjectNode().put("environmentCode", evaluation.environmentCode())
                .put("windowStart", evaluation.windowStart().toString()).put("windowEnd", evaluation.windowEnd().toString())
                .put("attemptCount", evaluation.attemptCount()).put("successCount", evaluation.successCount())
                .put("failureCount", evaluation.failureCount()).put("deadLetterCount", evaluation.deadLetterCount())
                .put("successRate", evaluation.successRate()).put("healthStatus", evaluation.healthStatus());
    }

    private String thresholdSnapshot(NotificationOperationsGovernanceService.EffectivePolicy policy,
            Long maintenanceWindowId) {
        ObjectNode snapshot = json.createObjectNode();
        if (policy.policyVersionId() != null) snapshot.put("policyVersionId", policy.policyVersionId());
        snapshot.put("policyVersionNo", policy.versionNo());
        snapshot.put("minimumOperationalAttempts", policy.minimumOperationalAttempts());
        snapshot.put("warningMinimumSuccessRate", policy.warningMinimumSuccessRate());
        snapshot.put("criticalMinimumSuccessRate", policy.criticalMinimumSuccessRate());
        snapshot.put("criticalEscalationAfter", policy.criticalEscalationAfter().toString());
        snapshot.put("repeatNotificationAfter", policy.repeatNotificationAfter().toString());
        snapshot.put("alertSuppressed", maintenanceWindowId != null);
        if (maintenanceWindowId != null) snapshot.put("maintenanceWindowId", maintenanceWindowId);
        snapshot.put("attemptOnlineRetention", properties.getAttemptOnlineRetention().toString());
        return canonicalJson.canonicalString(snapshot);
    }

    private static String health(NotificationOperationsSummary summary,
            NotificationOperationsGovernanceService.EffectivePolicy policy) {
        if (summary.attemptCount() < policy.minimumOperationalAttempts()) return "INSUFFICIENT_DATA";
        if (summary.successRate().compareTo(policy.criticalMinimumSuccessRate()) < 0) return "CRITICAL";
        if (summary.deadLetterCount() > 0
                || summary.successRate().compareTo(policy.warningMinimumSuccessRate()) < 0) return "WARNING";
        return "HEALTHY";
    }

    private void validateWindow(Instant start, Instant end) {
        if (start == null || end == null || !start.isBefore(end)
                || !Duration.between(start, end).equals(properties.getOperationsEvaluationWindow())) {
            throw new IllegalArgumentException("operations evaluation window does not match configured duration");
        }
        if (end.isAfter(clock.instant().plusSeconds(60))) {
            throw new IllegalArgumentException("operations evaluation window must not be in the future");
        }
    }

    private static String requiredEnvironment(String value) {
        String normalized = NotificationOperationsApplicationService.environment(value);
        if (normalized == null) throw new IllegalArgumentException("environmentCode must not be blank");
        return normalized;
    }

    private static String requiredActor(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("actor must not be blank");
        String normalized = value.trim();
        if (normalized.length() > 100) throw new IllegalArgumentException("actor is too long");
        return normalized;
    }

    private static int limit(int value) {
        if (value < 1 || value > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return value;
    }
}
