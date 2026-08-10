package com.ftk.tpip.control.api.notification;

import com.ftk.tpip.control.application.notification.NotificationCircuitOperationsService;
import com.ftk.tpip.control.application.notification.NotificationCircuitOperationsService.AssetType;
import com.ftk.tpip.control.application.notification.NotificationCircuitStatus;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/control/v1/notification-circuits")
public class NotificationCircuitController {
    private final NotificationCircuitOperationsService service;

    public NotificationCircuitController(NotificationCircuitOperationsService service) { this.service = service; }

    @GetMapping("/{assetType}/{assetId}")
    public NotificationCircuitStatus status(@PathVariable AssetType assetType,
            @PathVariable @Positive long assetId) {
        return service.status(assetType, assetId);
    }
}
