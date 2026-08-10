package com.ftk.tpip.control.api.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.release.WorkspaceVerificationApplicationService;
import com.ftk.tpip.release.domain.model.VerificationCheck;
import com.ftk.tpip.release.domain.model.VerificationCheckStatus;
import com.ftk.tpip.release.domain.model.VerificationRun;
import com.ftk.tpip.release.domain.model.VerificationRunType;
import com.ftk.tpip.release.domain.model.VerificationStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
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
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1")
public class WorkspaceVerificationController {
    private final WorkspaceVerificationApplicationService service;
    private final ObjectMapper json;

    public WorkspaceVerificationController(WorkspaceVerificationApplicationService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @PostMapping("/workspaces/{workspaceId}:verify")
    public ResponseEntity<VerificationJobResponse> verify(@PathVariable @Positive long workspaceId,
            @Valid @RequestBody VerifyWorkspace request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        VerificationRun run = service.verify(workspaceId, request.fixtureSuiteVersionId(), request.rowVersion(), actor);
        return ResponseEntity.created(URI.create("/control/v1/verification-jobs/" + run.id())).body(run(run));
    }

    @GetMapping("/workspaces/{workspaceId}/verification-jobs")
    public List<VerificationJobResponse> jobs(@PathVariable @Positive long workspaceId) {
        return service.list(workspaceId).stream().map(this::run).toList();
    }

    @GetMapping("/verification-jobs/{runId}")
    public VerificationJobResponse job(@PathVariable @Positive long runId) {
        return run(service.get(runId));
    }

    @GetMapping("/verification-jobs/{runId}/checks")
    public List<VerificationCheckResponse> checks(@PathVariable @Positive long runId) {
        return service.checks(runId).stream().map(this::check).toList();
    }

    @PostMapping("/verification-jobs/{runId}:retry")
    public ResponseEntity<VerificationJobResponse> retry(@PathVariable @Positive long runId,
            @Valid @RequestBody RetryVerification request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        VerificationRun retried = service.retry(runId, request.rowVersion(), actor);
        return ResponseEntity.created(URI.create("/control/v1/verification-jobs/" + retried.id()))
                .body(run(retried));
    }

    private VerificationJobResponse run(VerificationRun value) {
        return new VerificationJobResponse(value.id(), value.workspaceId(), value.runNo(), value.runType(),
                value.status(), value.totalCount(), value.passedCount(), value.failedCount(), value.evidenceUri(),
                read(value.resultSummary()), value.startedAt(), value.finishedAt());
    }

    private VerificationCheckResponse check(VerificationCheck value) {
        return new VerificationCheckResponse(value.id(), value.verificationRunId(), value.checkCode(), value.checkName(),
                value.status(), read(value.resultDetails()), read(value.evidenceDocument()), value.startedAt(),
                value.finishedAt());
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored verification JSON is invalid", failure);
        }
    }

    public record VerifyWorkspace(@Positive long fixtureSuiteVersionId, @PositiveOrZero long rowVersion) {}
    public record RetryVerification(@PositiveOrZero long rowVersion) {}

    public record VerificationJobResponse(long id, long workspaceId, long runNo, VerificationRunType runType,
            VerificationStatus status, int totalCount, int passedCount, int failedCount, String evidenceUri,
            JsonNode resultSummary, Instant startedAt, Instant finishedAt) {}

    public record VerificationCheckResponse(long id, long verificationRunId, String checkCode, String checkName,
            VerificationCheckStatus status, JsonNode resultDetails, JsonNode evidenceDocument,
            Instant startedAt, Instant finishedAt) {}
}
