package com.ftk.tpip.control.api.release;

import com.ftk.tpip.control.application.release.DriftGovernanceReminderObservabilityService;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/control/v1/verification-drift-workbench")
public class DriftGovernanceReminderObservabilityController {
    private final DriftGovernanceReminderObservabilityService service;

    public DriftGovernanceReminderObservabilityController(DriftGovernanceReminderObservabilityService service) {
        this.service = service;
    }

    @GetMapping("/governance-reminder-batches/{batchId}/diff")
    public DriftGovernanceReminderObservabilityService.BatchDiff diff(@PathVariable @Positive long batchId) {
        return service.diff(batchId);
    }

    @GetMapping("/governance-reminder-batches/{batchId}/delivery-status")
    public DriftGovernanceReminderObservabilityService.DeliveryStatus delivery(
            @PathVariable @Positive long batchId) {
        return service.delivery(batchId);
    }

    @GetMapping("/governance-reminder-batches/{batchId}/timeline")
    public DriftGovernanceReminderObservabilityService.BatchTimeline timeline(
            @PathVariable @Positive long batchId) {
        return service.timeline(batchId);
    }

    @GetMapping("/governance-reminder-metrics")
    public DriftGovernanceReminderObservabilityService.ReminderMetrics metrics(
            @RequestParam @Positive long workspaceId) {
        return service.metrics(workspaceId);
    }
}
