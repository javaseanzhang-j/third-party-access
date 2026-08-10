package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.VerificationDriftWorkbenchService;
import com.ftk.tpip.control.application.release.VerificationDriftGovernanceEvaluationService;
import com.ftk.tpip.control.application.release.VerificationDriftBulkOperationService;
import com.ftk.tpip.control.application.release.VerificationDriftOperationsMetricsService;
import com.ftk.tpip.control.application.release.VerificationDriftPolicyImpactService;
import com.ftk.tpip.control.application.release.DriftPolicyImpactSnapshotService;
import com.ftk.tpip.control.application.release.GlobalDriftPolicyImpactSnapshotService;
import com.ftk.tpip.control.application.release.GlobalDriftPolicyImpactJobService;
import com.ftk.tpip.release.domain.model.DriftPolicyImpactSnapshot;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactSnapshot;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJob;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository.Scope;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/verification-drift-workbench")
public class VerificationDriftWorkbenchController {
    private final VerificationDriftWorkbenchService service;
    private final VerificationDriftGovernanceEvaluationService governance;
    private final VerificationDriftBulkOperationService bulkOperations;
    private final VerificationDriftOperationsMetricsService operationsMetrics;
    private final VerificationDriftPolicyImpactService policyImpacts;
    private final DriftPolicyImpactSnapshotService impactSnapshots;
    private final GlobalDriftPolicyImpactSnapshotService globalImpactSnapshots;
    private final GlobalDriftPolicyImpactJobService globalImpactJobs;

    public VerificationDriftWorkbenchController(VerificationDriftWorkbenchService service,
            VerificationDriftGovernanceEvaluationService governance,
            VerificationDriftBulkOperationService bulkOperations,
            VerificationDriftOperationsMetricsService operationsMetrics,
            VerificationDriftPolicyImpactService policyImpacts,
            DriftPolicyImpactSnapshotService impactSnapshots,
            GlobalDriftPolicyImpactSnapshotService globalImpactSnapshots,
            GlobalDriftPolicyImpactJobService globalImpactJobs) {
        this.service = service;
        this.governance = governance;
        this.bulkOperations = bulkOperations;
        this.operationsMetrics = operationsMetrics;
        this.policyImpacts = policyImpacts;
        this.impactSnapshots = impactSnapshots;
        this.globalImpactSnapshots = globalImpactSnapshots;
        this.globalImpactJobs = globalImpactJobs;
    }

    @GetMapping("/reports")
    public VerificationDriftWorkbenchService.WorkbenchPage reports(
            @RequestParam(required = false) @Positive Long workspaceId,
            @RequestParam(defaultValue = "ACTIONABLE") Scope scope,
            @RequestParam(required = false) VerificationDriftKind driftKind,
            @RequestParam(required = false) @Size(min = 1, max = 180) String checkCode,
            @RequestParam(required = false) @Size(min = 1, max = 100) String assigneeCode,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "72") @Min(1) @Max(8760) int overdueAfterHours,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.reports(filter(workspaceId, scope, driftKind, checkCode, assigneeCode, overdueOnly, overdueAfterHours),
                page, size);
    }

    @GetMapping("/summary")
    public VerificationDriftWorkbenchService.WorkbenchSummary summary(
            @RequestParam(required = false) @Positive Long workspaceId,
            @RequestParam(defaultValue = "ALL") Scope scope,
            @RequestParam(required = false) VerificationDriftKind driftKind,
            @RequestParam(required = false) @Size(min = 1, max = 180) String checkCode,
            @RequestParam(required = false) @Size(min = 1, max = 100) String assigneeCode,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "72") @Min(1) @Max(8760) int overdueAfterHours) {
        return service.summary(filter(workspaceId, scope, driftKind, checkCode, assigneeCode, overdueOnly, overdueAfterHours));
    }

    @GetMapping("/groups")
    public List<VerificationDriftWorkbenchService.DriftGroup> groups(
            @RequestParam(required = false) @Positive Long workspaceId,
            @RequestParam(defaultValue = "ACTIONABLE") Scope scope,
            @RequestParam(required = false) VerificationDriftKind driftKind,
            @RequestParam(required = false) @Size(min = 1, max = 180) String checkCode,
            @RequestParam(required = false) @Size(min = 1, max = 100) String assigneeCode,
            @RequestParam(defaultValue = "false") boolean overdueOnly,
            @RequestParam(defaultValue = "72") @Min(1) @Max(8760) int overdueAfterHours,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.groups(filter(workspaceId, scope, driftKind, checkCode, assigneeCode, overdueOnly, overdueAfterHours), limit);
    }

    @GetMapping("/governance-evaluations")
    public VerificationDriftGovernanceEvaluationService.EvaluationPage governanceEvaluations(
            @RequestParam @Positive long workspaceId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return governance.evaluate(workspaceId, page, size);
    }

    @PostMapping("/governance-reviews:assign")
    public VerificationDriftBulkOperationService.BulkResult assign(@Valid @RequestBody AssignmentCommand request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String commandKey,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return bulkOperations.assign(commandKey, request.workspaceId(), request.dryRun(), request.assigneeCode(), request.reason(),
                items(request.items()), actor);
    }

    @PostMapping("/governance-reviews:acknowledge")
    public VerificationDriftBulkOperationService.BulkResult acknowledge(
            @Valid @RequestBody BulkDecisionCommand request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String commandKey,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return bulkOperations.acknowledge(commandKey, request.workspaceId(), request.dryRun(), request.reason(),
                items(request.items()), actor);
    }

    @PostMapping("/governance-reviews:dispose")
    public VerificationDriftBulkOperationService.BulkResult dispose(
            @Valid @RequestBody BulkDispositionCommand request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String commandKey,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return bulkOperations.dispose(commandKey, request.workspaceId(), request.resolution(), request.dryRun(), request.reason(),
                items(request.items()), actor);
    }

    @GetMapping("/governance-operations/{commandKey}")
    public VerificationDriftBulkOperationService.OperationEvidence operation(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 100) String commandKey) {
        return bulkOperations.get(commandKey);
    }

    @GetMapping("/governance-metrics")
    public VerificationDriftOperationsMetricsService.Metrics governanceMetrics(
            @RequestParam @Positive long workspaceId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(90) int windowDays,
            @RequestParam(required = false) @Min(1) @Max(8760) Integer slaHours) {
        return operationsMetrics.metrics(workspaceId, windowDays,
                slaHours == null ? null : Duration.ofHours(slaHours));
    }

    @GetMapping("/governance-policy-impact")
    public VerificationDriftPolicyImpactService.Impact governancePolicyImpact(
            @RequestParam @Positive long workspaceId,
            @RequestParam @Positive long candidatePolicyId,
            @RequestParam @Positive long candidateVersionId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return policyImpacts.compare(workspaceId, candidatePolicyId, candidateVersionId, page, size);
    }

    @PostMapping("/governance-policy-impact-snapshots")
    public DriftPolicyImpactSnapshot createPolicyImpactSnapshot(
            @Valid @RequestBody CreateImpactSnapshot request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return impactSnapshots.create(request.workspaceId(), request.candidatePolicyId(),
                request.candidateVersionId(), Duration.ofSeconds(request.ttlSeconds()), actor);
    }

    @GetMapping("/governance-policy-impact-snapshots/{snapshotId}")
    public DriftPolicyImpactSnapshot policyImpactSnapshot(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String snapshotId) {
        return impactSnapshots.get(snapshotId);
    }

    @PostMapping("/global-governance-policy-impact-snapshots")
    public GlobalDriftPolicyImpactSnapshot createGlobalPolicyImpactSnapshot(
            @Valid @RequestBody CreateGlobalImpactSnapshot request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return globalImpactSnapshots.create(request.candidatePolicyId(), request.candidateVersionId(),
                Duration.ofSeconds(request.ttlSeconds()), actor);
    }

    @GetMapping("/global-governance-policy-impact-snapshots/{snapshotId}")
    public GlobalDriftPolicyImpactSnapshot globalPolicyImpactSnapshot(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String snapshotId) {
        return globalImpactSnapshots.get(snapshotId);
    }

    @GetMapping("/global-governance-policy-impact-snapshots/{snapshotId}/workspace-snapshots")
    public GlobalDriftPolicyImpactSnapshotService.WorkspaceSnapshotPage globalPolicyImpactWorkspaceSnapshots(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String snapshotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return globalImpactSnapshots.workspaceSnapshots(snapshotId, page, size);
    }

    @PostMapping("/global-governance-policy-impact-jobs")
    public GlobalDriftPolicyImpactJob createGlobalPolicyImpactJob(
            @Valid @RequestBody CreateGlobalImpactSnapshot request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return globalImpactJobs.create(request.candidatePolicyId(), request.candidateVersionId(),
                Duration.ofSeconds(request.ttlSeconds()), actor);
    }

    @GetMapping("/global-governance-policy-impact-jobs/{jobId}")
    public GlobalDriftPolicyImpactJob globalPolicyImpactJob(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String jobId) {
        return globalImpactJobs.get(jobId);
    }

    @GetMapping("/global-governance-policy-impact-jobs/{jobId}/items")
    public GlobalDriftPolicyImpactJobService.ItemPage globalPolicyImpactJobItems(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String jobId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return globalImpactJobs.items(jobId, page, size);
    }

    @PostMapping("/global-governance-policy-impact-jobs/{jobId}:run-batch")
    public GlobalDriftPolicyImpactJobService.RunBatchResult runGlobalPolicyImpactJobBatch(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String jobId,
            @Valid @RequestBody RunImpactJobBatch request,
            @RequestHeader("X-Worker-Id") @NotBlank @Size(max = 100) String workerId) {
        return globalImpactJobs.runBatch(jobId, request.batchSize(), workerId);
    }

    @PostMapping("/global-governance-policy-impact-jobs/{jobId}:retry-failed")
    public GlobalDriftPolicyImpactJob retryGlobalPolicyImpactJob(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String jobId,
            @Valid @RequestBody RetryImpactJob request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return globalImpactJobs.retryFailed(jobId, request.rowVersion(), request.reason(), actor);
    }

    @PostMapping("/global-governance-policy-impact-jobs/{jobId}:seal")
    public GlobalDriftPolicyImpactJob sealGlobalPolicyImpactJob(
            @org.springframework.web.bind.annotation.PathVariable @NotBlank @Size(max = 36) String jobId,
            @Valid @RequestBody SealImpactJob request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return globalImpactJobs.seal(jobId, request.rowVersion(), actor);
    }

    private static List<VerificationDriftBulkOperationService.BulkItem> items(List<BulkTarget> values) {
        return values.stream().map(value -> new VerificationDriftBulkOperationService.BulkItem(
                value.reportId(), value.rowVersion())).toList();
    }

    private static VerificationDriftWorkbenchService.Filter filter(Long workspaceId, Scope scope,
            VerificationDriftKind driftKind, String checkCode, String assigneeCode,
            boolean overdueOnly, int overdueAfterHours) {
        return new VerificationDriftWorkbenchService.Filter(workspaceId, scope, driftKind, checkCode, assigneeCode, overdueOnly,
                Duration.ofHours(overdueAfterHours));
    }

    public record BulkTarget(@Positive long reportId, @PositiveOrZero long rowVersion) {}
    public record AssignmentCommand(@Positive long workspaceId, boolean dryRun,
            @NotBlank @Size(max = 100) String assigneeCode,
            @NotBlank @Size(max = 1000) String reason,
            @NotEmpty @Size(max = 100) List<@Valid BulkTarget> items) {}
    public record BulkDecisionCommand(@Positive long workspaceId, boolean dryRun,
            @NotBlank @Size(max = 1000) String reason,
            @NotEmpty @Size(max = 100) List<@Valid BulkTarget> items) {}
    public record BulkDispositionCommand(@Positive long workspaceId, boolean dryRun,
            @NotNull VerificationDriftReviewStatus resolution,
            @NotBlank @Size(max = 1000) String reason,
            @NotEmpty @Size(max = 100) List<@Valid BulkTarget> items) {}
    public record CreateImpactSnapshot(@Positive long workspaceId, @Positive long candidatePolicyId,
            @Positive long candidateVersionId, @Min(300) @Max(86400) long ttlSeconds) {}
    public record CreateGlobalImpactSnapshot(@Positive long candidatePolicyId,
            @Positive long candidateVersionId, @Min(300) @Max(86400) long ttlSeconds) {}
    public record RunImpactJobBatch(@Min(1) @Max(100) int batchSize) {}
    public record RetryImpactJob(@PositiveOrZero long rowVersion, @NotBlank @Size(max = 500) String reason) {}
    public record SealImpactJob(@PositiveOrZero long rowVersion) {}
}
