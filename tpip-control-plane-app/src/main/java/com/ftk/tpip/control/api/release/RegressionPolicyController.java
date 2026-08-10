package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.RegressionPolicyApplicationService;
import com.ftk.tpip.release.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/regression-policies")
public class RegressionPolicyController {
    private final RegressionPolicyApplicationService service;

    public RegressionPolicyController(RegressionPolicyApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody CreatePolicy request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        RegressionPolicy value = service.create(request.policyCode(), request.policyName(), request.baselineId(), actor);
        return ResponseEntity.created(URI.create("/control/v1/regression-policies/" + value.id()))
                .body(policy(value));
    }

    @GetMapping public List<PolicyResponse> list() { return service.list().stream().map(this::policy).toList(); }
    @GetMapping("/{policyId}") public PolicyResponse get(@PathVariable @Positive long policyId) {
        return policy(service.get(policyId));
    }

    @PostMapping("/{policyId}/versions")
    public ResponseEntity<VersionResponse> createVersion(@PathVariable @Positive long policyId,
            @Valid @RequestBody CreateVersion request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        RegressionPolicyVersion value = service.createVersion(policyId, request.baselineId(),
                Duration.ofSeconds(request.intervalSeconds()),
                Duration.ofSeconds(request.failureBackoffSeconds()), request.maximumConsecutiveFailures(), actor);
        return ResponseEntity.created(URI.create("/control/v1/regression-policies/" + policyId +
                "/versions/" + value.id())).body(version(value));
    }

    @GetMapping("/{policyId}/versions") public List<VersionResponse> versions(@PathVariable @Positive long policyId) {
        return service.versions(policyId).stream().map(this::version).toList();
    }

    @PostMapping("/{policyId}/versions/{versionId}:publish")
    public PolicyResponse publish(@PathVariable @Positive long policyId, @PathVariable @Positive long versionId,
            @Valid @RequestBody VersionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return policy(service.publish(policyId, versionId, request.rowVersion(), actor));
    }

    @PostMapping("/{policyId}:activate")
    public PolicyResponse activate(@PathVariable @Positive long policyId, @Valid @RequestBody ActivatePolicy request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return policy(service.activate(policyId, request.rowVersion(), request.firstRunAt(), actor));
    }

    @PostMapping("/{policyId}:pause")
    public PolicyResponse pause(@PathVariable @Positive long policyId, @Valid @RequestBody VersionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return policy(service.pause(policyId, request.rowVersion(), actor));
    }

    @GetMapping("/{policyId}/state") public StateResponse state(@PathVariable @Positive long policyId) {
        RegressionScheduleState value = service.state(policyId);
        return new StateResponse(value.policyId(), value.policyVersionId(), value.nextRunAt(), value.leaseOwner(),
                value.leaseUntil(), value.consecutiveFailures(), value.lastRunAt(), value.lastVerificationRunId(),
                value.lastDriftReportId(), value.lastOutcome(), value.lastError(), value.updatedAt());
    }

    private PolicyResponse policy(RegressionPolicy value) {
        return new PolicyResponse(value.id(), value.policyCode().value(), value.policyName(), value.baselineId(),
                value.status(), value.currentVersionId(), value.rowVersion(), value.createdAt(), value.updatedAt());
    }
    private VersionResponse version(RegressionPolicyVersion value) {
        return new VersionResponse(value.id(), value.policyId(), value.baselineId(), value.versionNo(),
                value.interval().toSeconds(),
                value.failureBackoff().toSeconds(), value.maximumConsecutiveFailures(), value.lifecycleStatus(),
                value.publishedAt(), value.createdAt());
    }

    public record CreatePolicy(@NotBlank @Size(max = 180) String policyCode,
            @NotBlank @Size(max = 200) String policyName, @Positive long baselineId) {}
    public record CreateVersion(@Positive Long baselineId, @Min(60) @Max(2592000) long intervalSeconds,
            @Min(60) @Max(86400) long failureBackoffSeconds,
            @Min(1) @Max(100) int maximumConsecutiveFailures) {}
    public record VersionCommand(@PositiveOrZero long rowVersion) {}
    public record ActivatePolicy(@PositiveOrZero long rowVersion, @NotNull Instant firstRunAt) {}
    public record PolicyResponse(long id, String policyCode, String policyName, long baselineId,
            RegressionPolicyStatus status, Long currentVersionId, long rowVersion,
            Instant createdAt, Instant updatedAt) {}
    public record VersionResponse(long id, long policyId, long baselineId, int versionNo, long intervalSeconds,
            long failureBackoffSeconds, int maximumConsecutiveFailures,
            RegressionPolicyVersionStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {}
    public record StateResponse(long policyId, long policyVersionId, Instant nextRunAt, String leaseOwner,
            Instant leaseUntil, int consecutiveFailures, Instant lastRunAt, Long lastVerificationRunId,
            Long lastDriftReportId, String lastOutcome, String lastError, Instant updatedAt) {}
}
