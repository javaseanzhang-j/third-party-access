package com.ftk.tpip.control.application.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationMaintenanceWindow;
import com.ftk.tpip.release.domain.model.NotificationOperationsPolicyVersion;
import com.ftk.tpip.release.domain.repository.NotificationOperationsGovernanceRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationOperationsGovernanceService {
    private static final Duration MAXIMUM_MAINTENANCE = Duration.ofDays(31);
    private final NotificationOperationsGovernanceRepository repository;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final NotificationDeliveryProperties properties;
    private final Clock clock;

    @Autowired
    public NotificationOperationsGovernanceService(NotificationOperationsGovernanceRepository repository,
            CanonicalJsonService canonicalJson, ObjectMapper json, NotificationDeliveryProperties properties) {
        this(repository, canonicalJson, json, properties, Clock.systemUTC());
    }

    NotificationOperationsGovernanceService(NotificationOperationsGovernanceRepository repository,
            CanonicalJsonService canonicalJson, ObjectMapper json, NotificationDeliveryProperties properties,
            Clock clock) {
        this.repository = repository; this.canonicalJson = canonicalJson; this.json = json;
        this.properties = properties; this.clock = clock;
    }

    @Transactional
    public NotificationOperationsPolicyVersion createPolicyVersion(String environmentCode, int minimumAttempts,
            BigDecimal warningRate, BigDecimal criticalRate, Duration criticalEscalationAfter,
            Duration repeatNotificationAfter, String actor) {
        String environment = environment(environmentCode); String operator = actor(actor);
        validatePolicy(minimumAttempts, warningRate, criticalRate, criticalEscalationAfter, repeatNotificationAfter);
        repository.lockEnvironment(environment);
        ObjectNode content = json.createObjectNode().put("environmentCode", environment)
                .put("minimumOperationalAttempts", minimumAttempts).put("warningMinimumSuccessRate", warningRate)
                .put("criticalMinimumSuccessRate", criticalRate)
                .put("criticalEscalationAfter", criticalEscalationAfter.toString())
                .put("repeatNotificationAfter", repeatNotificationAfter.toString());
        return repository.createPolicyVersion(new NotificationOperationsPolicyVersion(null, environment, 0,
                minimumAttempts, warningRate, criticalRate, criticalEscalationAfter, repeatNotificationAfter,
                "DRAFT", canonicalJson.sha256(canonicalJson.canonicalString(content)), operator,
                null, null, null));
    }

    @Transactional
    public NotificationOperationsPolicyVersion publishPolicyVersion(long id, String actor) {
        if (id <= 0) throw new IllegalArgumentException("policyVersionId must be positive");
        var current = repository.findPolicyVersion(id)
                .orElseThrow(() -> new IllegalArgumentException("operations policy does not exist"));
        repository.lockEnvironment(current.environmentCode());
        return repository.publishPolicyVersion(id, actor(actor), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<NotificationOperationsPolicyVersion> policies(String environmentCode) {
        return repository.findPolicyVersions(environment(environmentCode));
    }

    @Transactional
    public NotificationMaintenanceWindow scheduleMaintenance(String environmentCode, Instant start, Instant end,
            String reason, String actor) {
        String environment = environment(environmentCode); String operator = actor(actor);
        if (start == null || end == null || !start.isBefore(end) || !end.isAfter(clock.instant())
                || Duration.between(start, end).compareTo(MAXIMUM_MAINTENANCE) > 0) {
            throw new IllegalArgumentException("maintenance window must end in the future and be no longer than 31 days");
        }
        String normalizedReason = required(reason, "reason", 500);
        repository.lockEnvironment(environment);
        if (repository.hasOverlappingMaintenanceWindow(environment, start, end)) {
            throw new IllegalArgumentException("maintenance window overlaps an existing scheduled window");
        }
        return repository.createMaintenanceWindow(new NotificationMaintenanceWindow(null, environment, start, end,
                normalizedReason, "SCHEDULED", operator, null, null, null, null));
    }

    @Transactional
    public NotificationMaintenanceWindow cancelMaintenance(long id, String actor) {
        if (id <= 0) throw new IllegalArgumentException("maintenanceWindowId must be positive");
        return repository.cancelMaintenanceWindow(id, actor(actor), clock.instant());
    }

    @Transactional(readOnly = true)
    public List<NotificationMaintenanceWindow> maintenanceWindows(String environmentCode, int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        return repository.findMaintenanceWindows(environment(environmentCode), limit);
    }

    @Transactional(readOnly = true)
    public EffectivePolicy effectivePolicy(String environmentCode) {
        String environment = environment(environmentCode);
        return repository.findPublishedPolicy(environment).map(EffectivePolicy::from)
                .orElseGet(() -> new EffectivePolicy(null, 0, properties.getMinimumOperationalAttempts(),
                        properties.getWarningMinimumSuccessRate(), properties.getCriticalMinimumSuccessRate(),
                        Duration.ZERO, Duration.ZERO));
    }

    @Transactional(readOnly = true)
    public Optional<NotificationMaintenanceWindow> overlappingMaintenance(String environmentCode,
            Instant start, Instant end) {
        return repository.findOverlappingMaintenanceWindow(environment(environmentCode), start, end);
    }

    public record EffectivePolicy(Long policyVersionId, int versionNo, int minimumOperationalAttempts,
            BigDecimal warningMinimumSuccessRate, BigDecimal criticalMinimumSuccessRate,
            Duration criticalEscalationAfter, Duration repeatNotificationAfter) {
        static EffectivePolicy from(NotificationOperationsPolicyVersion value) {
            return new EffectivePolicy(value.id(), value.versionNo(), value.minimumOperationalAttempts(),
                    value.warningMinimumSuccessRate(), value.criticalMinimumSuccessRate(),
                    value.criticalEscalationAfter(), value.repeatNotificationAfter());
        }
    }

    private static void validatePolicy(int attempts, BigDecimal warning, BigDecimal critical,
            Duration escalation, Duration repeat) {
        if (attempts < 1 || warning == null || critical == null || warning.compareTo(BigDecimal.ZERO) < 0
                || warning.compareTo(BigDecimal.valueOf(100)) > 0 || critical.compareTo(BigDecimal.ZERO) < 0
                || critical.compareTo(warning) > 0 || escalation == null || escalation.isNegative()
                || escalation.compareTo(Duration.ofDays(31)) > 0 || repeat == null || repeat.isNegative()
                || repeat.compareTo(Duration.ofDays(31)) > 0) {
            throw new IllegalArgumentException("operations policy thresholds are invalid");
        }
    }
    private static String environment(String value) {
        String result = NotificationOperationsApplicationService.environment(value);
        if (result == null) throw new IllegalArgumentException("environmentCode must not be blank");
        return result;
    }
    private static String actor(String value) { return required(value, "X-Operator", 100); }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String result = value.trim(); if (result.length() > max) throw new IllegalArgumentException(field + " is too long");
        return result;
    }
}
