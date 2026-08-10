package com.ftk.tpip.control.api.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class NotificationAutomationControllerMappingTest {
    @Test
    void exposesActionStyleClaimPathWithoutAnExtraSlash() throws Exception {
        String base = NotificationAutomationController.class.getAnnotation(RequestMapping.class).value()[0];
        Method claim = NotificationAutomationController.class.getMethod("claim", String.class,
                NotificationAutomationController.ClaimRequest.class);
        String action = claim.getAnnotation(PostMapping.class).value()[0];

        assertEquals("/internal/v1/notification-deliveries:claim", base + action);
    }
}
