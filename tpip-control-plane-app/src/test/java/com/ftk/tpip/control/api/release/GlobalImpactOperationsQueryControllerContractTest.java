package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class GlobalImpactOperationsQueryControllerContractTest {
    @Test
    void exposesDedicatedStableOperationsView() throws Exception {
        assertEquals("/control/v1/verification-drift-workbench/global-governance-policy-impact-operations-view",
                GlobalImpactOperationsQueryController.class.getAnnotation(RequestMapping.class).value()[0]);
        var mapping = GlobalImpactOperationsQueryController.class.getMethod("overview", int.class)
                .getAnnotation(GetMapping.class);
        assertEquals(0, mapping.value().length);
    }
}
