package com.ftk.tpip.control.api.deployment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.deployment.DeploymentApplicationService;
import com.ftk.tpip.control.application.deployment.DeploymentHealthApplicationService;
import com.ftk.tpip.release.domain.model.DeploymentHealthAction;
import com.ftk.tpip.release.domain.model.DeploymentHealthDecision;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlert;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlertSeverity;
import com.ftk.tpip.release.domain.model.DeploymentHealthAlertStatus;
import com.ftk.tpip.release.domain.model.DeploymentHealthEvaluation;
import com.ftk.tpip.release.domain.model.DeploymentStatus;
import com.ftk.tpip.release.domain.model.IntegrationDeployment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/deployments")
public class DeploymentController {
    private final DeploymentApplicationService service;
    private final DeploymentHealthApplicationService health;
    private final ObjectMapper json;

    public DeploymentController(DeploymentApplicationService service,
            DeploymentHealthApplicationService health, ObjectMapper json) {
        this.service = service;
        this.health = health;
        this.json = json;
    }

    @PostMapping
    public ResponseEntity<DeploymentResponse> create(@Valid @RequestBody CreateDeployment request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        IntegrationDeployment created = service.create(request.deploymentCode(), request.bundleId(),
                request.rolloutMetadata(), actor);
        return ResponseEntity.created(URI.create("/control/v1/deployments/" + created.id()))
                .body(response(created));
    }

    @GetMapping("/{id}")
    public DeploymentResponse get(@PathVariable @Min(1) long id) { return response(service.get(id)); }

    @GetMapping
    public List<DeploymentResponse> list(@RequestParam @NotBlank String operationCode,
            @RequestParam @NotBlank String environmentCode) {
        return service.list(operationCode, environmentCode).stream().map(this::response).toList();
    }

    @PostMapping("/{id}:preheat")
    public DeploymentResponse preheat(@PathVariable @Min(1) long id, @Valid @RequestBody VersionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return response(service.preheat(id, request.rowVersion(), actor));
    }

    @PostMapping("/{id}:activate")
    public DeploymentResponse activate(@PathVariable @Min(1) long id, @Valid @RequestBody Activate request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return response(service.activate(id, request.rowVersion(), request.initialTraffic(), actor));
    }

    @PostMapping("/{id}:traffic")
    public DeploymentResponse traffic(@PathVariable @Min(1) long id, @Valid @RequestBody Traffic request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return response(service.increaseTraffic(id, request.rowVersion(), request.targetTraffic(), actor));
    }

    @PostMapping("/{id}:rollback")
    public DeploymentResponse rollback(@PathVariable @Min(1) long id, @Valid @RequestBody Rollback request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return response(service.rollback(id, request.rowVersion(), request.rollbackDeploymentCode(),
                request.reason(), actor));
    }

    @PostMapping("/{id}:evaluate-health")
    public HealthEvaluationResponse evaluateHealth(@PathVariable @Min(1) long id,
            @Valid @RequestBody EvaluateHealth request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return healthResponse(health.evaluate(id, request.windowStart(), request.windowEnd(),
                request.sampleCount(), request.failureCount(), request.p95LatencyMs(), request.evidence(), actor));
    }

    @GetMapping("/{id}/health-evaluations")
    public List<HealthEvaluationResponse> healthEvaluations(@PathVariable @Min(1) long id) {
        return health.list(id).stream().map(this::healthResponse).toList();
    }

    @GetMapping("/{id}/health-alerts")
    public List<HealthAlertResponse> healthAlerts(@PathVariable @Min(1) long id) {
        return health.listAlerts(id).stream().map(this::healthAlertResponse).toList();
    }

    @PostMapping("/{id}/health-alerts/{alertId}:acknowledge")
    public HealthAlertResponse acknowledgeHealthAlert(@PathVariable @Min(1) long id,
            @PathVariable @Min(1) long alertId,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return healthAlertResponse(health.acknowledgeAlert(id, alertId, actor));
    }

    private DeploymentResponse response(IntegrationDeployment deployment) {
        return new DeploymentResponse(deployment.id(), deployment.deploymentCode().value(), deployment.bundleId(),
                deployment.operationId(), deployment.environmentCode(), deployment.deploymentStatus(),
                deployment.trafficPercentage(), deployment.previousDeploymentId(), read(deployment.instanceStatus()),
                read(deployment.preheatEvidence()), read(deployment.rolloutMetadata()), deployment.rowVersion(),
                deployment.deployedBy(), deployment.deployedAt(), deployment.activatedAt(), deployment.endedAt(),
                deployment.updatedAt());
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored deployment JSON is invalid", exception); }
    }

    private HealthEvaluationResponse healthResponse(DeploymentHealthEvaluation value) {
        return new HealthEvaluationResponse(value.id(), value.deploymentId(), value.windowStart(), value.windowEnd(),
                value.sampleCount(), value.failureCount(), value.errorRatePercentage(), value.p95LatencyMs(),
                value.decision(), value.action(), value.rollbackDeploymentId(), read(value.evidence()),
                value.evaluatedBy(), value.createdAt());
    }

    private HealthAlertResponse healthAlertResponse(DeploymentHealthAlert value) {
        return new HealthAlertResponse(value.id(), value.deploymentId(), value.evaluationId(), value.alertCode(),
                value.severity(), value.status(), value.summary(), read(value.details()), value.acknowledgedBy(),
                value.acknowledgedAt(), value.resolvedAt(), value.createdAt(), value.updatedAt());
    }

    public record CreateDeployment(
            @NotBlank @Size(max = 200) String deploymentCode,
            @Positive long bundleId,
            JsonNode rolloutMetadata) {}
    public record VersionCommand(@PositiveOrZero long rowVersion) {}
    public record Activate(@PositiveOrZero long rowVersion,
            @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal initialTraffic) {}
    public record Traffic(@PositiveOrZero long rowVersion,
            @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal targetTraffic) {}
    public record Rollback(@PositiveOrZero long rowVersion,
            @NotBlank @Size(max = 200) String rollbackDeploymentCode,
            @NotBlank @Size(max = 2000) String reason) {}
    public record EvaluateHealth(Instant windowStart, Instant windowEnd,
            @PositiveOrZero long sampleCount, @PositiveOrZero long failureCount,
            @PositiveOrZero long p95LatencyMs, JsonNode evidence) {}
    public record HealthEvaluationResponse(long id, long deploymentId, Instant windowStart, Instant windowEnd,
            long sampleCount, long failureCount, BigDecimal errorRatePercentage, long p95LatencyMs,
            DeploymentHealthDecision decision, DeploymentHealthAction action, Long rollbackDeploymentId,
            JsonNode evidence, String evaluatedBy, Instant createdAt) {}
    public record HealthAlertResponse(long id, long deploymentId, long evaluationId, String alertCode,
            DeploymentHealthAlertSeverity severity, DeploymentHealthAlertStatus status, String summary,
            JsonNode details, String acknowledgedBy, Instant acknowledgedAt, Instant resolvedAt,
            Instant createdAt, Instant updatedAt) {}
    public record DeploymentResponse(long id, String deploymentCode, long bundleId, long operationId,
            String environmentCode, DeploymentStatus deploymentStatus, BigDecimal trafficPercentage,
            Long previousDeploymentId, JsonNode instanceStatus, JsonNode preheatEvidence,
            JsonNode rolloutMetadata, long rowVersion, String deployedBy, Instant deployedAt,
            Instant activatedAt, Instant endedAt, Instant updatedAt) {}
}
