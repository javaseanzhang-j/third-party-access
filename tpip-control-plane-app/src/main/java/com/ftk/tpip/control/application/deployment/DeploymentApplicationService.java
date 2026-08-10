package com.ftk.tpip.control.application.deployment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.application.release.BundleNotFoundException;
import com.ftk.tpip.release.domain.exception.DeploymentConcurrentModificationException;
import com.ftk.tpip.release.domain.exception.DeploymentLifecycleException;
import com.ftk.tpip.release.domain.model.BundleLifecycleStatus;
import com.ftk.tpip.release.domain.model.DeploymentBundleAsset;
import com.ftk.tpip.release.domain.model.DeploymentStatus;
import com.ftk.tpip.release.domain.model.IntegrationDeployment;
import com.ftk.tpip.release.domain.repository.DeploymentRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.shared.AssetCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeploymentApplicationService {
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final DeploymentRepository deployments;
    private final ReleaseRepository releases;
    private final CanonicalOperationRepository operations;
    private final RuntimePreheatClient preheatClient;
    private final CanonicalJsonService canonicalJson;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public DeploymentApplicationService(DeploymentRepository deployments, ReleaseRepository releases,
            CanonicalOperationRepository operations, RuntimePreheatClient preheatClient,
            CanonicalJsonService canonicalJson, ObjectMapper json) {
        this(deployments, releases, operations, preheatClient, canonicalJson, json, Clock.systemUTC());
    }

    DeploymentApplicationService(DeploymentRepository deployments, ReleaseRepository releases,
            CanonicalOperationRepository operations, RuntimePreheatClient preheatClient,
            CanonicalJsonService canonicalJson, ObjectMapper json, Clock clock) {
        this.deployments = deployments;
        this.releases = releases;
        this.operations = operations;
        this.preheatClient = preheatClient;
        this.canonicalJson = canonicalJson;
        this.json = json;
        this.clock = clock;
    }

    @Transactional
    public IntegrationDeployment create(String deploymentCode, long bundleId, JsonNode rolloutMetadata, String actor) {
        DeploymentBundleAsset bundle = publishedBundle(bundleId);
        deployments.lockOperation(bundle.operationId());
        List<IntegrationDeployment> active = deployments.findActive(bundle.operationId(), bundle.environmentCode());
        if (active.size() > 1) throw new IllegalArgumentException("Another canary rollout is already active");
        if (!active.isEmpty() && active.getFirst().bundleId() == bundleId) {
            throw new IllegalArgumentException("Bundle is already active in this environment");
        }
        Long previous = active.isEmpty() ? null : active.getFirst().id();
        IntegrationDeployment pending = IntegrationDeployment.pending(AssetCode.of(deploymentCode), bundleId,
                bundle.operationId(), bundle.environmentCode(), previous,
                object(rolloutMetadata, "rolloutMetadata"), actor(actor));
        return deployments.create(pending, actor(actor));
    }

    public IntegrationDeployment preheat(long id, long rowVersion, String actor) {
        IntegrationDeployment current = get(id);
        if (current.rowVersion() != rowVersion) throw new DeploymentConcurrentModificationException(id, rowVersion);
        if (current.deploymentStatus() != DeploymentStatus.PENDING
                && current.deploymentStatus() != DeploymentStatus.PREHEAT_FAILED) {
            throw new DeploymentLifecycleException(id, "preheat requires PENDING or PREHEAT_FAILED");
        }
        IntegrationDeployment started = deployments.transition(id, rowVersion, current.deploymentStatus(),
                DeploymentStatus.PREHEATING, ZERO, current.instanceStatus(), current.preheatEvidence(),
                current.rolloutMetadata(), null, null, actor(actor));
        DeploymentBundleAsset bundle = publishedBundle(started.bundleId());
        var operation = operations.findById(started.operationId()).orElseThrow();
        RuntimePreheatClient.PreheatReport report = preheatClient.preheat(new RuntimePreheatClient.PreheatCommand(
                started.deploymentCode().value(), operation.operationCode().value(), started.environmentCode(),
                bundle.bundleCode(), bundle.bundleVersion(), bundle.artifactChecksum()));
        String evidence = canonicalJson.canonicalString(json.valueToTree(report));
        ObjectNode status = json.createObjectNode();
        status.put("requiredInstances", report.requiredInstances());
        status.put("successfulInstances", report.successfulInstances());
        status.put("quorumReached", report.quorumReached());
        return deployments.transition(id, started.rowVersion(), DeploymentStatus.PREHEATING,
                report.quorumReached() ? DeploymentStatus.READY : DeploymentStatus.PREHEAT_FAILED,
                ZERO, canonicalJson.canonicalString(status), evidence, started.rolloutMetadata(),
                null, null, actor(actor));
    }

    @Transactional
    public IntegrationDeployment activate(long id, long rowVersion, BigDecimal initialTraffic, String actor) {
        IntegrationDeployment beforeLock = get(id);
        deployments.lockOperation(beforeLock.operationId());
        IntegrationDeployment target = get(id);
        checkVersion(target, rowVersion);
        if (target.deploymentStatus() != DeploymentStatus.READY) {
            throw new DeploymentLifecycleException(id, "activation requires READY");
        }
        BigDecimal percentage = percentage(initialTraffic);
        if (percentage.signum() <= 0) throw new IllegalArgumentException("initialTraffic must be greater than 0");
        List<IntegrationDeployment> active = deployments.findActive(target.operationId(), target.environmentCode());
        if (target.previousDeploymentId() == null) {
            if (!active.isEmpty() || percentage.compareTo(HUNDRED) != 0) {
                throw new IllegalArgumentException("First deployment must activate at 100% traffic");
            }
        } else {
            if (active.size() != 1 || !target.previousDeploymentId().equals(active.getFirst().id())) {
                throw new DeploymentLifecycleException(id, "previous active deployment changed; create a new deployment");
            }
        }
        Instant now = clock.instant();
        IntegrationDeployment activated = deployments.transition(id, rowVersion, DeploymentStatus.READY,
                DeploymentStatus.ACTIVE, percentage, target.instanceStatus(), target.preheatEvidence(),
                target.rolloutMetadata(), now, null, actor(actor));
        if (target.previousDeploymentId() != null) {
            IntegrationDeployment previous = active.getFirst();
            BigDecimal remaining = HUNDRED.subtract(percentage);
            deployments.transition(previous.id(), previous.rowVersion(), DeploymentStatus.ACTIVE,
                    remaining.signum() == 0 ? DeploymentStatus.DEPRECATED : DeploymentStatus.ACTIVE,
                    remaining, previous.instanceStatus(), previous.preheatEvidence(), previous.rolloutMetadata(),
                    previous.activatedAt(), remaining.signum() == 0 ? now : null, actor(actor));
        }
        return activated;
    }

    @Transactional
    public IntegrationDeployment increaseTraffic(long id, long rowVersion, BigDecimal targetTraffic, String actor) {
        IntegrationDeployment beforeLock = get(id);
        deployments.lockOperation(beforeLock.operationId());
        IntegrationDeployment target = get(id);
        checkVersion(target, rowVersion);
        if (target.deploymentStatus() != DeploymentStatus.ACTIVE || target.previousDeploymentId() == null) {
            throw new DeploymentLifecycleException(id, "traffic change requires an ACTIVE canary deployment");
        }
        BigDecimal percentage = percentage(targetTraffic);
        if (percentage.compareTo(target.trafficPercentage()) <= 0) {
            throw new IllegalArgumentException("targetTraffic must increase monotonically; use rollback to reduce traffic");
        }
        IntegrationDeployment previous = get(target.previousDeploymentId());
        if (previous.deploymentStatus() != DeploymentStatus.ACTIVE) {
            throw new DeploymentLifecycleException(id, "previous deployment is no longer ACTIVE");
        }
        Instant now = clock.instant();
        IntegrationDeployment updated = deployments.transition(id, rowVersion, DeploymentStatus.ACTIVE,
                DeploymentStatus.ACTIVE, percentage, target.instanceStatus(), target.preheatEvidence(),
                target.rolloutMetadata(), target.activatedAt(), null, actor(actor));
        BigDecimal remaining = HUNDRED.subtract(percentage);
        deployments.transition(previous.id(), previous.rowVersion(), DeploymentStatus.ACTIVE,
                remaining.signum() == 0 ? DeploymentStatus.DEPRECATED : DeploymentStatus.ACTIVE,
                remaining, previous.instanceStatus(), previous.preheatEvidence(), previous.rolloutMetadata(),
                previous.activatedAt(), remaining.signum() == 0 ? now : null, actor(actor));
        return updated;
    }

    @Transactional
    public IntegrationDeployment rollback(long id, long rowVersion, String rollbackDeploymentCode,
            String reason, String actor) {
        IntegrationDeployment beforeLock = get(id);
        deployments.lockOperation(beforeLock.operationId());
        IntegrationDeployment target = get(id);
        checkVersion(target, rowVersion);
        if (target.deploymentStatus() != DeploymentStatus.ACTIVE || target.previousDeploymentId() == null) {
            throw new DeploymentLifecycleException(id, "rollback requires an ACTIVE deployment with a previous version");
        }
        IntegrationDeployment previous = get(target.previousDeploymentId());
        if (previous.deploymentStatus() != DeploymentStatus.ACTIVE
                && previous.deploymentStatus() != DeploymentStatus.DEPRECATED) {
            throw new DeploymentLifecycleException(id, "previous deployment is not rollback eligible");
        }
        Instant now = clock.instant();
        deployments.transition(target.id(), rowVersion, DeploymentStatus.ACTIVE, DeploymentStatus.ROLLED_BACK,
                ZERO, target.instanceStatus(), target.preheatEvidence(), target.rolloutMetadata(),
                target.activatedAt(), now, actor(actor));
        if (previous.deploymentStatus() == DeploymentStatus.ACTIVE) {
            deployments.transition(previous.id(), previous.rowVersion(), DeploymentStatus.ACTIVE,
                    DeploymentStatus.DEPRECATED, ZERO, previous.instanceStatus(), previous.preheatEvidence(),
                    previous.rolloutMetadata(), previous.activatedAt(), now, actor(actor));
        }
        ObjectNode metadata = json.createObjectNode();
        metadata.put("action", "ROLLBACK");
        metadata.put("rolledBackDeploymentId", target.id());
        metadata.put("reason", required(reason, "reason", 2000));
        IntegrationDeployment rollback = new IntegrationDeployment(null, AssetCode.of(rollbackDeploymentCode),
                previous.bundleId(), previous.operationId(), previous.environmentCode(), DeploymentStatus.ACTIVE,
                HUNDRED, target.id(), previous.instanceStatus(), previous.preheatEvidence(),
                canonicalJson.canonicalString(metadata), 0, actor(actor), null, now, null, null);
        return deployments.create(rollback, actor(actor));
    }

    @Transactional(readOnly = true)
    public IntegrationDeployment get(long id) {
        return deployments.findById(id).orElseThrow(() -> new DeploymentNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<IntegrationDeployment> list(String operationCode, String environmentCode) {
        var operation = operations.findByCode(AssetCode.of(operationCode))
                .orElseThrow(() -> new IllegalArgumentException("operationCode does not exist"));
        return deployments.findByOperationAndEnvironment(operation.id(), environmentCode);
    }

    @Transactional(readOnly = true)
    public List<IntegrationDeployment> healthCandidates() {
        return deployments.findActiveCanaries();
    }

    @Transactional(readOnly = true)
    public ActiveRoute activeRoute(String operationCode, String environmentCode) {
        var operation = operations.findByCode(AssetCode.of(operationCode))
                .orElseThrow(() -> new IllegalArgumentException("operationCode does not exist"));
        List<IntegrationDeployment> active = deployments.findActive(operation.id(), environmentCode);
        if (active.isEmpty()) throw new ActiveRouteNotFoundException(operationCode, environmentCode);
        BigDecimal total = active.stream().map(IntegrationDeployment::trafficPercentage)
                .reduce(ZERO, BigDecimal::add);
        if (total.compareTo(HUNDRED) != 0) throw new IllegalStateException("Active deployment traffic must total 100%");
        List<RouteTarget> targets = active.stream().map(deployment -> {
            DeploymentBundleAsset bundle = publishedBundle(deployment.bundleId());
            return new RouteTarget(deployment.id(), deployment.deploymentCode().value(), bundle.bundleCode(),
                    bundle.bundleVersion(), bundle.artifactChecksum(), deployment.trafficPercentage());
        }).toList();
        String material = canonicalJson.canonicalString(json.valueToTree(targets));
        return new ActiveRoute(operationCode, environmentCode, canonicalJson.sha256(material), targets);
    }

    private DeploymentBundleAsset publishedBundle(long id) {
        DeploymentBundleAsset bundle = releases.findBundle(id).orElseThrow(() -> new BundleNotFoundException(Long.toString(id)));
        if (bundle.lifecycleStatus() != BundleLifecycleStatus.PUBLISHED) {
            throw new IllegalArgumentException("Deployment requires a PUBLISHED Bundle");
        }
        return bundle;
    }
    private static void checkVersion(IntegrationDeployment deployment, long rowVersion) {
        if (deployment.rowVersion() != rowVersion) {
            throw new DeploymentConcurrentModificationException(deployment.id(), rowVersion);
        }
    }
    private String object(JsonNode value, String field) {
        if (value == null || value.isNull()) return "{}";
        if (!value.isObject()) throw new IllegalArgumentException(field + " must be a JSON object");
        return canonicalJson.canonicalString(value);
    }
    private static BigDecimal percentage(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("traffic percentage is required");
        try {
            BigDecimal normalized = value.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.signum() < 0 || normalized.compareTo(HUNDRED) > 0) {
                throw new IllegalArgumentException("traffic percentage must be between 0 and 100");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("traffic percentage supports at most two decimals", exception);
        }
    }
    private static String actor(String value) { return required(value, "actor", 100); }
    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }

    public record ActiveRoute(String operationCode, String environmentCode, String revision,
                              List<RouteTarget> targets) {}
    public record RouteTarget(long deploymentId, String deploymentCode, String bundleCode,
                              String bundleVersion, String artifactChecksum, BigDecimal trafficPercentage) {}
}
