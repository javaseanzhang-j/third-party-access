package com.ftk.tpip.control.api.notification;

import com.ftk.tpip.control.application.notification.NotificationProviderGovernance;
import com.ftk.tpip.release.domain.model.NotificationProviderCapability;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/control/v1/notification-provider-capabilities")
public class NotificationProviderCapabilityController {
    private final NotificationProviderGovernance governance;
    public NotificationProviderCapabilityController(NotificationProviderGovernance governance) {
        this.governance = governance;
    }
    @GetMapping public List<NotificationProviderCapability> list() { return governance.capabilities(); }
}
