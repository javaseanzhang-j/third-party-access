package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.DriftGovernancePolicyAssetQueryService;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyScope;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.Set;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/verification-drift-workbench/drift-governance-policy-asset-views")
public class DriftGovernancePolicyAssetQueryController {
    private final DriftGovernancePolicyAssetQueryService queries;

    public DriftGovernancePolicyAssetQueryController(DriftGovernancePolicyAssetQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public DriftGovernancePolicyAssetQueryService.PolicyPage policies(
            @RequestParam(required = false) Set<DriftGovernancePolicyScope> scope,
            @RequestParam(required = false) Set<DriftGovernancePolicyStatus> status,
            @RequestParam(required = false) @Positive Long workspaceId,
            @RequestParam(required = false) @Size(min = 1, max = 180) String keyword,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.policies(new DriftGovernancePolicyAssetQueryService.PolicyFilter(
                scope, status, workspaceId, keyword), page, size);
    }

    @GetMapping("/{policyId}")
    public DriftGovernancePolicyAssetQueryService.PolicyDetail policy(
            @PathVariable @Positive long policyId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int recentJobLimit) {
        return queries.policy(policyId, recentJobLimit);
    }

    @GetMapping("/{policyId}/versions/{versionId}")
    public DriftGovernancePolicyAssetQueryService.VersionDetail version(
            @PathVariable @Positive long policyId, @PathVariable @Positive long versionId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int recentJobLimit) {
        return queries.version(policyId, versionId, recentJobLimit);
    }
}
