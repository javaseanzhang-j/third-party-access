package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.GlobalImpactJobSchedulingService;
import com.ftk.tpip.release.domain.model.*;
import jakarta.validation.Valid;import jakarta.validation.constraints.*;import java.util.*;
import org.springframework.validation.annotation.Validated;import org.springframework.web.bind.annotation.*;

@Validated @RestController
@RequestMapping("/control/v1/verification-drift-workbench/global-governance-policy-impact-jobs")
public class GlobalImpactJobSchedulingController{
 private final GlobalImpactJobSchedulingService scheduling;public GlobalImpactJobSchedulingController(GlobalImpactJobSchedulingService scheduling){this.scheduling=scheduling;}
 @GetMapping("/{jobId}/runtime-state") public GlobalImpactJobRuntimeState state(@PathVariable @NotBlank @Size(max=36) String jobId){return scheduling.state(jobId);}
 @GetMapping("/stalled") public List<GlobalImpactJobRuntimeState> stalled(@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit){return scheduling.stalled(limit);}
 @GetMapping("/slo-summary") public SloSummary summary(@RequestParam(defaultValue="100") @Min(1) @Max(500) int limit){var items=scheduling.stalled(limit);long critical=items.stream().filter(x->x.stalledSeconds()>=scheduling.criticalStallThreshold().toSeconds()).count();
  var recommendations=items.stream().collect(java.util.stream.Collectors.groupingBy(GlobalImpactJobRuntimeState::recoveryRecommendation,java.util.stream.Collectors.counting()));return new SloSummary(items.size(),critical,items.size()-critical,recommendations);}
 @PostMapping("/{jobId}:reprioritize") public GlobalImpactJobRuntimeState reprioritize(@PathVariable @NotBlank @Size(max=36) String jobId,@Valid @RequestBody Reprioritize command,@RequestHeader("X-Operator") @NotBlank @Size(max=100) String actor){return scheduling.reprioritize(jobId,command.rowVersion(),command.priority(),actor,command.reason());}
 public record Reprioritize(@PositiveOrZero long rowVersion,@NotNull GlobalImpactJobPriority priority,@NotBlank @Size(max=500) String reason){}
 public record SloSummary(long stalledCount,long criticalCount,long warningCount,Map<String,Long> recoveryRecommendations){}
}
