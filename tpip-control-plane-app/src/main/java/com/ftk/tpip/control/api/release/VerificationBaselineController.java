package com.ftk.tpip.control.api.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.release.VerificationBaselineApplicationService;
import com.ftk.tpip.control.application.release.VerificationBaselineAnalysisService;
import com.ftk.tpip.release.domain.model.VerificationBaseline;
import com.ftk.tpip.release.domain.model.VerificationDriftReport;
import com.ftk.tpip.release.domain.model.VerificationDriftReview;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftStatus;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1")
public class VerificationBaselineController {
    private final VerificationBaselineApplicationService service;
    private final VerificationBaselineAnalysisService analysis;
    private final ObjectMapper json;

    public VerificationBaselineController(VerificationBaselineApplicationService service,
            VerificationBaselineAnalysisService analysis, ObjectMapper json) {
        this.service = service;
        this.analysis = analysis;
        this.json = json;
    }

    @PostMapping("/verification-baselines")
    public ResponseEntity<BaselineResponse> create(@Valid @RequestBody CreateBaseline request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        VerificationBaseline value = service.create(request.sourceVerificationRunId(), actor);
        return ResponseEntity.created(URI.create("/control/v1/verification-baselines/" + value.id()))
                .body(baseline(value));
    }

    @GetMapping("/verification-baselines/{baselineId}")
    public BaselineResponse get(@PathVariable @Positive long baselineId) {
        return baseline(service.get(baselineId));
    }

    @GetMapping("/verification-baselines")
    public List<BaselineResponse> list(@RequestParam @Positive long workspaceId) {
        return service.list(workspaceId).stream().map(this::baseline).toList();
    }

    @GetMapping("/verification-baselines/{baselineId}/lineage")
    public VerificationBaselineAnalysisService.BaselineLineage lineage(@PathVariable @Positive long baselineId) {
        return analysis.lineage(baselineId);
    }

    @GetMapping("/verification-baselines/{baselineId}/drift-trend")
    public VerificationBaselineAnalysisService.BaselineDriftTrend driftTrend(
            @PathVariable @Positive long baselineId) {
        return analysis.driftTrend(baselineId);
    }

    @GetMapping("/verification-baselines/{baselineId}/impact")
    public VerificationBaselineAnalysisService.BaselinePolicyImpact impact(@PathVariable @Positive long baselineId) {
        return analysis.impact(baselineId);
    }

    @PostMapping("/verification-baselines/{baselineId}:run")
    public RegressionResponse run(@PathVariable @Positive long baselineId,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var result = service.run(baselineId, actor);
        return new RegressionResponse(run(result.verificationRun()), report(result.driftReport()));
    }

    @PostMapping("/verification-baselines/{baselineId}:compare")
    public ResponseEntity<DriftReportResponse> compare(@PathVariable @Positive long baselineId,
            @Valid @RequestBody CompareRun request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        VerificationDriftReport value = service.compare(baselineId, request.verificationRunId(), actor);
        return ResponseEntity.created(URI.create("/control/v1/verification-drift-reports/" + value.id()))
                .body(report(value));
    }

    @GetMapping("/verification-baselines/{baselineId}/drift-reports")
    public List<DriftReportResponse> reports(@PathVariable @Positive long baselineId) {
        return service.reports(baselineId).stream().map(this::report).toList();
    }

    @GetMapping("/verification-drift-reports/{reportId}")
    public DriftReportResponse report(@PathVariable @Positive long reportId) {
        return report(service.report(reportId));
    }

    @GetMapping("/verification-drift-reports/{reportId}/review")
    public DriftReviewResponse review(@PathVariable @Positive long reportId) {
        return review(service.review(reportId));
    }

    @PostMapping("/verification-drift-reports/{reportId}:acknowledge")
    public DriftReviewResponse acknowledge(@PathVariable @Positive long reportId,
            @Valid @RequestBody DecisionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return review(service.acknowledge(reportId, request.rowVersion(), request.reason(), actor));
    }

    @PostMapping("/verification-drift-reports/{reportId}:dismiss")
    public DriftReviewResponse dismiss(@PathVariable @Positive long reportId,
            @Valid @RequestBody DecisionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return review(service.dismiss(reportId, request.rowVersion(), request.reason(), actor));
    }

    @PostMapping("/verification-drift-reports/{reportId}:accept")
    public DriftAcceptanceResponse accept(@PathVariable @Positive long reportId,
            @Valid @RequestBody DecisionCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.accept(reportId, request.rowVersion(), request.reason(), actor);
        return new DriftAcceptanceResponse(review(value.review()), baseline(value.successorBaseline()));
    }

    private BaselineResponse baseline(VerificationBaseline value) {
        return new BaselineResponse(value.id(), value.workspaceId(), value.fixtureSuiteVersionId(),
                value.sourceVerificationRunId(), value.baselineChecksum(), read(value.snapshotDocument()),
                value.predecessorBaselineId(), value.acceptedDriftReportId(), value.createdAt());
    }

    private static DriftReviewResponse review(VerificationDriftReview value) {
        return new DriftReviewResponse(value.driftReportId(), value.status(), value.rowVersion(),
                value.assigneeCode(), value.assignedBy(), value.assignedAt(), value.assignmentNote(),
                value.acknowledgedBy(), value.acknowledgedAt(), value.acknowledgmentNote(), value.resolvedBy(),
                value.resolvedAt(), value.resolutionReason(), value.successorBaselineId(), value.createdAt(),
                value.updatedAt());
    }

    private DriftReportResponse report(VerificationDriftReport value) {
        return new DriftReportResponse(value.id(), value.baselineId(), value.verificationRunId(),
                value.driftStatus(), value.comparedCheckCount(), value.driftCount(),
                read(value.reportDocument()), value.createdAt());
    }

    private static RegressionRunResponse run(VerificationRun value) {
        return new RegressionRunResponse(value.id(), value.workspaceId(), value.runNo(), value.runType(),
                value.status(), value.totalCount(), value.passedCount(), value.failedCount(), value.evidenceUri(),
                value.startedAt(), value.finishedAt());
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored verification baseline JSON is invalid", failure);
        }
    }

    public record CreateBaseline(@Positive long sourceVerificationRunId) {}
    public record CompareRun(@Positive long verificationRunId) {}
    public record BaselineResponse(long id, long workspaceId, long fixtureSuiteVersionId,
            long sourceVerificationRunId, String baselineChecksum, JsonNode snapshot,
            Long predecessorBaselineId, Long acceptedDriftReportId, Instant createdAt) {}
    public record DriftReportResponse(long id, long baselineId, long verificationRunId,
            VerificationDriftStatus driftStatus, int comparedCheckCount, int driftCount,
            JsonNode report, Instant createdAt) {}
    public record RegressionRunResponse(long id, long workspaceId, long runNo, VerificationRunType runType,
            VerificationStatus status, int totalCount, int passedCount, int failedCount, String evidenceUri,
            Instant startedAt, Instant finishedAt) {}
    public record RegressionResponse(RegressionRunResponse verificationRun, DriftReportResponse driftReport) {}
    public record DecisionCommand(@PositiveOrZero long rowVersion,
            @NotBlank @Size(max = 1000) String reason) {}
    public record DriftReviewResponse(long driftReportId, VerificationDriftReviewStatus status, long rowVersion,
            String assigneeCode, String assignedBy, Instant assignedAt, String assignmentNote,
            String acknowledgedBy, Instant acknowledgedAt, String acknowledgmentNote,
            String resolvedBy, Instant resolvedAt, String resolutionReason, Long successorBaselineId,
            Instant createdAt, Instant updatedAt) {}
    public record DriftAcceptanceResponse(DriftReviewResponse review, BaselineResponse successorBaseline) {}
}
