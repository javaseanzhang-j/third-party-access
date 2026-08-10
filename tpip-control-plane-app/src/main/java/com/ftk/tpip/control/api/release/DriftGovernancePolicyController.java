package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.DriftGovernancePolicyApplicationService;
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
@RequestMapping("/control/v1")
public class DriftGovernancePolicyController {
    private final DriftGovernancePolicyApplicationService service;
    public DriftGovernancePolicyController(DriftGovernancePolicyApplicationService service) { this.service = service; }

    @PostMapping("/drift-governance-policies")
    public ResponseEntity<PolicyResponse> create(@Valid @RequestBody CreatePolicy request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.create(request.policyCode(), request.policyName(), request.scope(), request.workspaceId(), actor);
        return ResponseEntity.created(URI.create("/control/v1/drift-governance-policies/" + value.id()))
                .body(policy(value));
    }
    @GetMapping("/drift-governance-policies") public List<PolicyResponse> list() { return service.list().stream().map(this::policy).toList(); }
    @GetMapping("/drift-governance-policies/{policyId}") public PolicyResponse get(@PathVariable @Positive long policyId) {
        return policy(service.get(policyId));
    }
    @PostMapping("/drift-governance-policies/{policyId}/versions")
    public ResponseEntity<VersionResponse> createVersion(@PathVariable @Positive long policyId,
            @Valid @RequestBody CreateVersion request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.createVersion(policyId, Duration.ofSeconds(request.overdueAfterSeconds()),
                Duration.ofSeconds(request.aggregationWindowSeconds()),
                Duration.ofSeconds(request.reminderIntervalSeconds()), request.maximumReminders(),
                request.ownerCode(), request.suppressedDriftKinds(), request.suppressedCheckCodes(), actor);
        return ResponseEntity.created(URI.create("/control/v1/drift-governance-policies/" + policyId +
                "/versions/" + value.id())).body(version(value));
    }
    @GetMapping("/drift-governance-policies/{policyId}/versions") public List<VersionResponse> versions(@PathVariable @Positive long policyId) {
        return service.versions(policyId).stream().map(this::version).toList();
    }
    @PostMapping("/drift-governance-policies/{policyId}/versions/{versionId}:publish")
    public PolicyResponse publish(@PathVariable @Positive long policyId, @PathVariable @Positive long versionId,
            @Valid @RequestBody VersionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return policy(service.publish(policyId, versionId, request.rowVersion(), request.impactSnapshotId(), actor));
    }
    @PostMapping("/drift-governance-policies/{policyId}:activate")
    public PolicyResponse activate(@PathVariable @Positive long policyId, @Valid @RequestBody VersionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return policy(service.activate(policyId, request.rowVersion(), request.impactSnapshotId(), actor));
    }
    @PostMapping("/drift-governance-policies/{policyId}:pause")
    public PolicyResponse pause(@PathVariable @Positive long policyId, @Valid @RequestBody VersionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return policy(service.pause(policyId, request.rowVersion(), actor));
    }
    @GetMapping("/drift-governance-policies:resolve")
    public ResolvedResponse resolve(@RequestParam @Positive long workspaceId) {
        var value = service.resolve(workspaceId);
        return new ResolvedResponse(workspaceId, value.source(), value.policyId(), value.policyVersionId(),
                value.overdueAfter().toSeconds(), value.aggregationWindow().toSeconds(),
                value.reminderInterval().toSeconds(), value.maximumReminders(), value.ownerCode(),
                value.suppressedDriftKinds(), value.suppressedCheckCodes());
    }

    private PolicyResponse policy(DriftGovernancePolicy value) {
        return new PolicyResponse(value.id(), value.policyCode().value(), value.policyName(), value.scope(),
                value.workspaceId(), value.status(), value.currentVersionId(), value.rowVersion(),
                value.createdAt(), value.updatedAt());
    }
    private VersionResponse version(DriftGovernancePolicyVersion value) {
        return new VersionResponse(value.id(), value.policyId(), value.versionNo(), value.overdueAfter().toSeconds(),
                value.aggregationWindow().toSeconds(), value.reminderInterval().toSeconds(),
                value.maximumReminders(), value.ownerCode(), value.suppressedDriftKinds(),
                value.suppressedCheckCodes(), value.contentChecksum(), value.lifecycleStatus(),
                value.publishedAt(), value.createdAt());
    }

    public record CreatePolicy(@NotBlank @Size(max = 180) String policyCode,
            @NotBlank @Size(max = 200) String policyName, @NotNull DriftGovernancePolicyScope scope,
            @Positive Long workspaceId) {}
    public record CreateVersion(@Min(3600) @Max(31536000) long overdueAfterSeconds,
            @Min(3600) @Max(2592000) long aggregationWindowSeconds,
            @Min(3600) @Max(2592000) long reminderIntervalSeconds,
            @Min(1) @Max(100) int maximumReminders, @NotBlank @Size(max = 100) String ownerCode,
            @NotNull @Size(max = 5) List<@NotNull VerificationDriftKind> suppressedDriftKinds,
            @NotNull @Size(max = 100) List<@NotBlank @Size(max = 180) String> suppressedCheckCodes) {}
    public record VersionCommand(@PositiveOrZero long rowVersion, @Size(max = 36) String impactSnapshotId) {}
    public record PolicyResponse(long id, String policyCode, String policyName, DriftGovernancePolicyScope scope,
            Long workspaceId, DriftGovernancePolicyStatus status, Long currentVersionId, long rowVersion,
            Instant createdAt, Instant updatedAt) {}
    public record VersionResponse(long id, long policyId, int versionNo, long overdueAfterSeconds,
            long aggregationWindowSeconds, long reminderIntervalSeconds, int maximumReminders, String ownerCode,
            List<VerificationDriftKind> suppressedDriftKinds, List<String> suppressedCheckCodes,
            String contentChecksum, DriftGovernancePolicyVersionStatus lifecycleStatus,
            Instant publishedAt, Instant createdAt) {}
    public record ResolvedResponse(long workspaceId,
            DriftGovernancePolicyApplicationService.ResolutionSource source, Long policyId, Long policyVersionId,
            long overdueAfterSeconds, long aggregationWindowSeconds, long reminderIntervalSeconds,
            int maximumReminders, String ownerCode, List<VerificationDriftKind> suppressedDriftKinds,
            List<String> suppressedCheckCodes) {}
}
