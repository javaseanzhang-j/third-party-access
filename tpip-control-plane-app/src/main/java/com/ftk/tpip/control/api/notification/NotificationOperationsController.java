package com.ftk.tpip.control.api.notification;

import com.ftk.tpip.control.application.notification.NotificationOperationsApplicationService;
import com.ftk.tpip.control.application.notification.NotificationOperationsAutomationService;
import com.ftk.tpip.control.application.notification.NotificationOperationsSummary;
import com.ftk.tpip.release.domain.model.NotificationOperationsAlert;
import com.ftk.tpip.release.domain.model.NotificationOperationsEvaluation;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/notification-operations")
public class NotificationOperationsController {
    private final NotificationOperationsApplicationService service;
    private final NotificationOperationsAutomationService automation;

    public NotificationOperationsController(NotificationOperationsApplicationService service,
            NotificationOperationsAutomationService automation) {
        this.service = service;
        this.automation = automation;
    }

    @GetMapping("/summary")
    public NotificationOperationsSummary summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String environmentCode,
            @RequestParam(required = false) String channelCode,
            @RequestParam(required = false) NotificationProviderType providerType,
            @RequestParam(required = false) @Positive Long endpointRevisionId) {
        return service.summary(from, to, environmentCode, channelCode, providerType, endpointRevisionId);
    }

    @GetMapping("/evaluations")
    public java.util.List<NotificationOperationsEvaluation> evaluations(
            @RequestParam String environmentCode,
            @RequestParam(defaultValue = "100") @Positive int limit) {
        return automation.listEvaluations(environmentCode, limit);
    }

    @GetMapping("/alerts")
    public java.util.List<NotificationOperationsAlert> alerts(
            @RequestParam String environmentCode,
            @RequestParam(defaultValue = "100") @Positive int limit) {
        return automation.listAlerts(environmentCode, limit);
    }

    @PostMapping("/alerts/{alertId}:acknowledge")
    public NotificationOperationsAlert acknowledge(@PathVariable @Positive long alertId,
            @RequestParam String environmentCode,
            @RequestHeader("X-Operator") String operator) {
        return automation.acknowledgeAlert(environmentCode, alertId, operator);
    }
}
