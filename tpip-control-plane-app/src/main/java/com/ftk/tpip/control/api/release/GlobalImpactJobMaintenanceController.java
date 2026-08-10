package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.*;
import com.ftk.tpip.release.domain.model.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Duration;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs")
public class GlobalImpactJobMaintenanceController {
    private final GlobalDriftPolicyImpactJobService jobs;
    private final GlobalImpactJobMaintenanceService maintenance;
    private final GlobalImpactJobMaintenanceOrchestrator orchestrator;
    public GlobalImpactJobMaintenanceController(GlobalDriftPolicyImpactJobService jobs,
            GlobalImpactJobMaintenanceService maintenance,GlobalImpactJobMaintenanceOrchestrator orchestrator){
        this.jobs=jobs;this.maintenance=maintenance;this.orchestrator=orchestrator;}
    @PostMapping("/{jobId}:cancel")
    public GlobalDriftPolicyImpactJob cancel(@PathVariable @NotBlank @Size(max=36) String jobId,
            @Valid @RequestBody CancelCommand request,@RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){
        return jobs.cancel(jobId,request.rowVersion(),request.reason(),actor);}
    @GetMapping("/maintenance-candidates")
    public List<GlobalDriftPolicyImpactJob> candidates(@RequestParam(defaultValue="7") @Min(1) @Max(3650) int retentionDays,
            @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit){return maintenance.candidates(Duration.ofDays(retentionDays),limit);}
    @PostMapping("/maintenance:run")
    public GlobalImpactJobMaintenanceOrchestrator.Result run(@Valid @RequestBody MaintenanceCommand request,
            @RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){
        return orchestrator.run(Duration.ofDays(request.retentionDays()),request.limit(),request.deleteOrphanSnapshots(),
                request.dryRun(),actor,request.reason(),request.expireDue());}
    @GetMapping("/{jobId}/purge-receipt")
    public GlobalDriftPolicyImpactJobPurgeReceipt receipt(@PathVariable @NotBlank @Size(max=36) String jobId){
        return maintenance.receipt(jobId);}
    public record CancelCommand(@PositiveOrZero long rowVersion,@NotBlank @Size(max=500) String reason){}
    public record MaintenanceCommand(boolean dryRun,boolean expireDue,boolean deleteOrphanSnapshots,
            @Min(1) @Max(3650) int retentionDays,@Min(1) @Max(100) int limit,
            @NotBlank @Size(max=500) String reason){}
}
