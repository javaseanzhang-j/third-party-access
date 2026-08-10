package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.GlobalImpactJobQueryService;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobItemStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.WorkspaceRiskLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/verification-drift-workbench/global-governance-policy-impact-job-views")
public class GlobalImpactJobQueryController {
    private final GlobalImpactJobQueryService queries;

    public GlobalImpactJobQueryController(GlobalImpactJobQueryService queries) { this.queries = queries; }

    @GetMapping
    public GlobalImpactJobQueryService.JobPage jobs(
            @RequestParam(required = false) Set<GlobalDriftPolicyImpactJobStatus> status,
            @RequestParam(required = false) Set<GlobalImpactJobPriority> priority,
            @RequestParam(required = false) @Positive Long candidatePolicyId,
            @RequestParam(required = false) @Size(min = 1, max = 100) String createdBy,
            @RequestParam(required = false) @Size(min = 1, max = 180) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant createdTo,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.jobs(new GlobalImpactJobQueryService.JobFilter(status, priority, candidatePolicyId,
                createdBy, keyword, createdFrom, createdTo), page, size);
    }

    @GetMapping("/{jobId}")
    public GlobalImpactJobQueryService.JobDetail job(
            @PathVariable @Size(min = 1, max = 36) String jobId) {
        return queries.job(jobId);
    }

    @GetMapping("/{jobId}/workspace-impacts")
    public GlobalImpactJobQueryService.WorkspaceImpactPage workspaceImpacts(
            @PathVariable @Size(min = 1, max = 36) String jobId,
            @RequestParam(required = false) Set<GlobalDriftPolicyImpactJobItemStatus> status,
            @RequestParam(required = false) Set<WorkspaceRiskLevel> riskLevel,
            @RequestParam(required = false) @Size(min = 1, max = 180) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.workspaceImpacts(jobId, status, riskLevel, keyword, page, size);
    }

    @GetMapping("/{jobId}/impact-summary")
    public GlobalImpactJobQueryService.WorkspaceImpactSummary workspaceImpactSummary(
            @PathVariable @Size(min = 1, max = 36) String jobId) {
        return queries.workspaceImpactSummary(jobId);
    }

    @GetMapping("/{jobId}/timeline")
    public GlobalImpactJobQueryService.Timeline timeline(
            @PathVariable @Size(min = 1, max = 36) String jobId,
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return queries.timeline(jobId, limit);
    }
}
