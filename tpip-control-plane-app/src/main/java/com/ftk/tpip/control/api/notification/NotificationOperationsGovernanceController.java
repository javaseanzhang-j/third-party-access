package com.ftk.tpip.control.api.notification;

import com.ftk.tpip.control.application.notification.NotificationOperationsGovernanceService;
import com.ftk.tpip.release.domain.model.NotificationMaintenanceWindow;
import com.ftk.tpip.release.domain.model.NotificationOperationsPolicyVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
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
@RequestMapping("/control/v1/notification-operations-governance")
public class NotificationOperationsGovernanceController {
    private final NotificationOperationsGovernanceService service;

    public NotificationOperationsGovernanceController(NotificationOperationsGovernanceService service) {
        this.service = service;
    }

    @PostMapping("/policy-versions")
    public ResponseEntity<NotificationOperationsPolicyVersion> createPolicy(@Valid @RequestBody PolicyRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.createPolicyVersion(request.environmentCode(), request.minimumOperationalAttempts(),
                request.warningMinimumSuccessRate(), request.criticalMinimumSuccessRate(),
                request.criticalEscalationAfter(), request.repeatNotificationAfter(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-operations-governance/policy-versions/"
                + value.id())).body(value);
    }

    @GetMapping("/policy-versions")
    public List<NotificationOperationsPolicyVersion> policies(@RequestParam String environmentCode) {
        return service.policies(environmentCode);
    }

    @PostMapping("/policy-versions/{id}:publish")
    public NotificationOperationsPolicyVersion publishPolicy(@PathVariable @Positive long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.publishPolicyVersion(id, actor);
    }

    @PostMapping("/maintenance-windows")
    public ResponseEntity<NotificationMaintenanceWindow> schedule(@Valid @RequestBody MaintenanceRequest request,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        var value = service.scheduleMaintenance(request.environmentCode(), request.windowStart(), request.windowEnd(),
                request.reason(), actor);
        return ResponseEntity.created(URI.create("/control/v1/notification-operations-governance/maintenance-windows/"
                + value.id())).body(value);
    }

    @GetMapping("/maintenance-windows")
    public List<NotificationMaintenanceWindow> maintenance(@RequestParam String environmentCode,
            @RequestParam(defaultValue = "100") @Positive int limit) {
        return service.maintenanceWindows(environmentCode, limit);
    }

    @PostMapping("/maintenance-windows/{id}:cancel")
    public NotificationMaintenanceWindow cancel(@PathVariable @Positive long id,
            @RequestHeader("X-Operator") @NotBlank @Size(max = 100) String actor) {
        return service.cancelMaintenance(id, actor);
    }

    public record PolicyRequest(@NotBlank @Size(max = 32) String environmentCode,
            @Min(1) int minimumOperationalAttempts,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal warningMinimumSuccessRate,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal criticalMinimumSuccessRate,
            @NotNull Duration criticalEscalationAfter,
            @NotNull Duration repeatNotificationAfter) { }

    public record MaintenanceRequest(@NotBlank @Size(max = 32) String environmentCode,
            @NotNull Instant windowStart, @NotNull Instant windowEnd,
            @NotBlank @Size(max = 500) String reason) { }
}
