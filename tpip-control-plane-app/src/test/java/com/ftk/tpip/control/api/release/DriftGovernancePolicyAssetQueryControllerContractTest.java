package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DriftGovernancePolicyAssetQueryControllerContractTest {
    @Test
    void exposesStablePolicyAssetListDetailAndVersionRoutes() throws Exception {
        assertEquals("/control/v1/verification-drift-workbench/drift-governance-policy-asset-views",
                DriftGovernancePolicyAssetQueryController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertEquals(0, DriftGovernancePolicyAssetQueryController.class
                .getMethod("policies", java.util.Set.class, java.util.Set.class, Long.class,
                        String.class, int.class, int.class).getAnnotation(GetMapping.class).value().length);
        assertEquals("/{policyId}", DriftGovernancePolicyAssetQueryController.class
                .getMethod("policy", long.class, int.class).getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/{policyId}/versions/{versionId}", DriftGovernancePolicyAssetQueryController.class
                .getMethod("version", long.class, long.class, int.class)
                .getAnnotation(GetMapping.class).value()[0]);
    }
}
