package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.deployment.HealthAutomationAuthenticator;
import com.ftk.tpip.control.application.release.GlobalDriftPolicyImpactJobService;
import com.ftk.tpip.control.application.release.GlobalImpactJobSchedulingService;
import com.ftk.tpip.release.domain.model.GlobalImpactJobDispatch;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJob;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/internal/v1")
public class GlobalDriftPolicyImpactAutomationController {
    private final GlobalDriftPolicyImpactJobService jobs;
    private final HealthAutomationAuthenticator authenticator;
    private final GlobalImpactJobSchedulingService scheduling;

    public GlobalDriftPolicyImpactAutomationController(GlobalDriftPolicyImpactJobService jobs,
            HealthAutomationAuthenticator authenticator,GlobalImpactJobSchedulingService scheduling) {
        this.jobs = jobs;
        this.authenticator = authenticator;
        this.scheduling=scheduling;
    }

    @PostMapping("/global-drift-policy-impact-jobs:claim-runnable")
    public List<WorkerDispatch> claimRunnable(
            @RequestHeader(value="Authorization",required=false) String authorization,
            @RequestHeader("X-Worker-Id") @NotBlank @Size(max=100) String workerId,
            @Valid @RequestBody ClaimRunnable request){authenticator.authenticate(authorization);return scheduling.claim(workerId,request.limit()).stream()
                    .map(value->new WorkerDispatch(value.job().jobId(),value.job().status().name(),value.job().expiresAt(),
                            value.recommendedBatchSize())).toList();}

    @GetMapping("/global-drift-policy-impact-jobs/runnable")
    public List<GlobalDriftPolicyImpactJob> runnable(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        authenticator.authenticate(authorization);
        return jobs.runnable(limit);
    }

    @PostMapping("/global-drift-policy-impact-jobs/{jobId}:run-batch")
    public GlobalDriftPolicyImpactJobService.RunBatchResult runBatch(
            @PathVariable @NotBlank @Size(max = 36) String jobId,
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader("X-Worker-Id") @NotBlank @Size(max = 100) String workerId,
            @Valid @RequestBody RunBatch request) {
        authenticator.authenticate(authorization);
        return scheduling.runReserved(jobId, request.batchSize(), workerId,jobs);
    }

    public record RunBatch(@Min(1) @Max(100) int batchSize) {}
    public record ClaimRunnable(@Min(1) @Max(50) int limit) {}
    public record WorkerDispatch(String jobId,String status,java.time.Instant expiresAt,int recommendedBatchSize){}
}
