package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.GlobalImpactOperationsQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/verification-drift-workbench/global-governance-policy-impact-operations-view")
public class GlobalImpactOperationsQueryController {
    private final GlobalImpactOperationsQueryService queries;

    public GlobalImpactOperationsQueryController(GlobalImpactOperationsQueryService queries) {
        this.queries = queries;
    }

    @GetMapping
    public GlobalImpactOperationsQueryService.OperationsOverview overview(
            @RequestParam(defaultValue = "100") @Min(1) @Max(500) int limit) {
        return queries.overview(limit);
    }
}
