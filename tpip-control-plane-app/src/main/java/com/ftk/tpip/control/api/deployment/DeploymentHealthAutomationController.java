package com.ftk.tpip.control.api.deployment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.deployment.DeploymentApplicationService;
import com.ftk.tpip.control.application.deployment.DeploymentHealthApplicationService;
import com.ftk.tpip.control.application.deployment.HealthAutomationAuthenticator;
import com.ftk.tpip.release.domain.model.DeploymentHealthAction;
import com.ftk.tpip.release.domain.model.DeploymentHealthDecision;
import com.ftk.tpip.release.domain.model.DeploymentHealthEvaluation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/v1/deployment-health")
public class DeploymentHealthAutomationController {
    private final DeploymentApplicationService deployments;
    private final DeploymentHealthApplicationService health;
    private final HealthAutomationAuthenticator authenticator;
    private final ObjectMapper json;

    public DeploymentHealthAutomationController(DeploymentApplicationService deployments,
            DeploymentHealthApplicationService health, HealthAutomationAuthenticator authenticator,
            ObjectMapper json) {
        this.deployments = deployments;
        this.health = health;
        this.authenticator = authenticator;
        this.json = json;
    }

    @GetMapping("/candidates")
    public List<CandidateResponse> candidates(@RequestHeader("Authorization") String authorization) {
        authenticator.authenticate(authorization);
        return deployments.healthCandidates().stream().map(value -> new CandidateResponse(value.id(),
                value.deploymentCode().value(), value.operationId(), value.environmentCode(),
                value.trafficPercentage(), value.activatedAt())).toList();
    }

    @PostMapping("/{id}:evaluate")
    public EvaluationResponse evaluate(@PathVariable @Min(1) long id,
            @RequestHeader("Authorization") String authorization, @Valid @RequestBody EvaluateRequest request) {
        authenticator.authenticate(authorization);
        return response(health.evaluate(id, request.windowStart(), request.windowEnd(), request.sampleCount(),
                request.failureCount(), request.p95LatencyMs(), request.evidence(), "system-health-worker"));
    }

    private EvaluationResponse response(DeploymentHealthEvaluation value) {
        return new EvaluationResponse(value.id(), value.deploymentId(), value.decision(), value.action(),
                value.rollbackDeploymentId(), value.windowStart(), value.windowEnd(), read(value.evidence()));
    }

    private JsonNode read(String value) {
        try { return json.readTree(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Stored evidence is invalid", exception); }
    }

    public record CandidateResponse(long deploymentId, String deploymentCode, long operationId,
            String environmentCode, BigDecimal trafficPercentage, Instant activatedAt) {}
    public record EvaluateRequest(Instant windowStart, Instant windowEnd,
            @PositiveOrZero long sampleCount, @PositiveOrZero long failureCount,
            @PositiveOrZero long p95LatencyMs, JsonNode evidence) {}
    public record EvaluationResponse(long id, long deploymentId, DeploymentHealthDecision decision,
            DeploymentHealthAction action, Long rollbackDeploymentId, Instant windowStart,
            Instant windowEnd, JsonNode evidence) {}
}
