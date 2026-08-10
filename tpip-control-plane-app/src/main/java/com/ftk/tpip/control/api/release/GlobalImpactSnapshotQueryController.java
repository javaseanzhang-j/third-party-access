package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.GlobalImpactSnapshotQueryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/verification-drift-workbench/global-governance-policy-impact-snapshot-views")
public class GlobalImpactSnapshotQueryController {
    private final GlobalImpactSnapshotQueryService queries;

    public GlobalImpactSnapshotQueryController(GlobalImpactSnapshotQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/{snapshotId}")
    public GlobalImpactSnapshotQueryService.SnapshotDetail snapshot(
            @PathVariable @Size(min = 1, max = 36) String snapshotId) {
        return queries.snapshot(snapshotId);
    }

    @GetMapping("/{snapshotId}/workspace-snapshots")
    public GlobalImpactSnapshotQueryService.WorkspaceSnapshotPage workspaceSnapshots(
            @PathVariable @Size(min = 1, max = 36) String snapshotId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.workspaceSnapshots(snapshotId, page, size);
    }
}
