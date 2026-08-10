package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.DriftGovernanceReminderBatchApplicationService;
import com.ftk.tpip.release.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1")
public class DriftGovernanceReminderBatchController {
    private final DriftGovernanceReminderBatchApplicationService service;
    public DriftGovernanceReminderBatchController(DriftGovernanceReminderBatchApplicationService service) {
        this.service = service;
    }

    @PostMapping("/verification-drift-workbench/governance-reminder-batches")
    public ResponseEntity<DetailResponse> create(@Valid @RequestBody CreateRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.create(request.workspaceId(), request.environmentCode(), request.executionIds(), actor);
        return ResponseEntity.created(URI.create("/control/v1/verification-drift-workbench/" +
                "governance-reminder-batches/" + value.batch().id())).body(detail(value));
    }
    @PostMapping("/verification-drift-workbench/governance-reminder-batches/{batchId}:approve")
    public DetailResponse approve(@PathVariable @Positive long batchId, @Valid @RequestBody Command request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return detail(service.approve(batchId, request.rowVersion(), actor));
    }
    @PostMapping("/verification-drift-workbench/governance-reminder-batches/{batchId}:cancel")
    public DetailResponse cancel(@PathVariable @Positive long batchId, @Valid @RequestBody CancelCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return detail(service.cancel(batchId, request.rowVersion(), request.reason(), actor));
    }
    @PostMapping("/verification-drift-workbench/governance-reminder-batches/{batchId}:replace")
    public ReplacementResponse replace(@PathVariable @Positive long batchId,
            @Valid @RequestBody ReplaceCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.replace(batchId, request.rowVersion(), request.reason(), request.environmentCode(),
                request.executionIds(), actor);
        return new ReplacementResponse(detail(value.cancelled()), detail(value.replacement()));
    }
    @PostMapping("/verification-drift-workbench/governance-reminder-batches/{batchId}:dispatch")
    public DetailResponse dispatch(@PathVariable @Positive long batchId, @Valid @RequestBody Command request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return detail(service.dispatch(batchId, request.rowVersion(), actor));
    }
    @GetMapping("/verification-drift-workbench/governance-reminder-batches")
    public List<BatchResponse> list(@RequestParam @Positive long workspaceId) {
        return service.list(workspaceId).stream().map(this::batch).toList();
    }
    @GetMapping("/verification-drift-workbench/governance-reminder-batches/{batchId}")
    public DetailResponse get(@PathVariable @Positive long batchId) { return detail(service.get(batchId)); }

    private DetailResponse detail(DriftGovernanceReminderBatchApplicationService.BatchDetail value) {
        return new DetailResponse(batch(value.batch()), value.members().stream().map(member ->
                new MemberResponse(member.executionId(), member.reminderNo(), member.evaluationChecksum())).toList());
    }
    private BatchResponse batch(DriftGovernanceReminderBatch value) {
        return new BatchResponse(value.id(), value.batchCode(), value.workspaceId(), value.aggregationKey(),
                value.environmentCode(), value.ownerCode(), value.creationSource(), value.status(), value.memberCount(),
                value.contentChecksum(), value.rowVersion(), value.outboxId(), value.replacesBatchId(),
                value.replacedByBatchId(), value.createdBy(), value.createdAt(), value.approvedBy(), value.approvedAt(),
                value.cancelReason(), value.cancelledBy(), value.cancelledAt(), value.dispatchedBy(), value.dispatchedAt());
    }

    public record CreateRequest(@Positive long workspaceId,
            @NotBlank @Size(max = 32) String environmentCode,
            @NotEmpty @Size(max = 100) List<@Positive Long> executionIds) {}
    public record Command(@PositiveOrZero long rowVersion) {}
    public record CancelCommand(@PositiveOrZero long rowVersion,
            @NotBlank @Size(max = 500) String reason) {}
    public record ReplaceCommand(@PositiveOrZero long rowVersion, @NotBlank @Size(max = 500) String reason,
            @NotBlank @Size(max = 32) String environmentCode,
            @NotEmpty @Size(max = 100) List<@Positive Long> executionIds) {}
    public record MemberResponse(long executionId, int reminderNo, String evaluationChecksum) {}
    public record DetailResponse(BatchResponse batch, List<MemberResponse> members) {}
    public record BatchResponse(long id, String batchCode, long workspaceId, String aggregationKey,
            String environmentCode, String ownerCode, DriftGovernanceReminderBatchSource creationSource,
            DriftGovernanceReminderBatchStatus status, int memberCount, String contentChecksum, long rowVersion,
            Long outboxId, Long replacesBatchId, Long replacedByBatchId, String createdBy, Instant createdAt,
            String approvedBy, Instant approvedAt, String cancelReason, String cancelledBy, Instant cancelledAt,
            String dispatchedBy, Instant dispatchedAt) {}
    public record ReplacementResponse(DetailResponse cancelled, DetailResponse replacement) {}
}
