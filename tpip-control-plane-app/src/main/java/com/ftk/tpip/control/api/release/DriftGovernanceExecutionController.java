package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.DriftGovernanceExecutionApplicationService;
import com.ftk.tpip.release.domain.model.DriftGovernanceExecution;
import com.ftk.tpip.release.domain.model.DriftGovernanceExecutionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1")
public class DriftGovernanceExecutionController {
    private final DriftGovernanceExecutionApplicationService service;

    public DriftGovernanceExecutionController(DriftGovernanceExecutionApplicationService service) {
        this.service = service;
    }

    @PostMapping("/verification-drift-workbench/governance-executions:materialize")
    public ResponseEntity<Response> materialize(@Valid @RequestBody MaterializeRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.materialize(request.workspaceId(), request.reportId(), actor);
        return ResponseEntity.created(URI.create("/control/v1/verification-drift-workbench/governance-executions/" +
                value.id())).body(response(value));
    }

    @GetMapping("/verification-drift-workbench/governance-executions")
    public List<Response> list(@RequestParam @Positive long workspaceId) {
        return service.list(workspaceId).stream().map(this::response).toList();
    }

    @GetMapping("/verification-drift-workbench/governance-executions:due")
    public List<Response> due(@RequestParam @Positive long workspaceId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int limit) {
        return service.dueCandidates(workspaceId, limit).stream().map(this::response).toList();
    }

    @GetMapping("/verification-drift-workbench/governance-executions/{executionId}")
    public Response get(@PathVariable @Positive long executionId) {
        return response(service.get(executionId));
    }

    private Response response(DriftGovernanceExecution value) {
        return new Response(value.id(), value.driftReportId(), value.workspaceId(), value.policySource(),
                value.policyId(), value.policyVersionId(), value.aggregationKey(), value.ownerCode(), value.status(),
                value.maximumReminders(), value.reminderIntervalSeconds(), value.reminderCount(), value.nextReminderAt(), value.evaluationChecksum(),
                value.rowVersion(), value.materializedBy(), value.materializedAt(), value.updatedAt());
    }

    public record MaterializeRequest(@Positive long workspaceId, @Positive long reportId) {}
    public record Response(long id, long driftReportId, long workspaceId, String policySource, Long policyId,
            Long policyVersionId, String aggregationKey, String ownerCode, DriftGovernanceExecutionStatus status,
            int maximumReminders, long reminderIntervalSeconds, int reminderCount, Instant nextReminderAt, String evaluationChecksum,
            long rowVersion, String materializedBy, Instant materializedAt, Instant updatedAt) {}
}
