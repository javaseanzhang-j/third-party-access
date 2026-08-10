package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class GlobalDriftPolicyImpactAutomationControllerContractTest {
    @Test void exposesTokenProtectedDiscoveryAndBatchPaths() throws Exception {
        String base = GlobalDriftPolicyImpactAutomationController.class
                .getAnnotation(RequestMapping.class).value()[0];
        Method runnable = GlobalDriftPolicyImpactAutomationController.class
                .getMethod("runnable", String.class, int.class);
        Method batch = GlobalDriftPolicyImpactAutomationController.class
                .getMethod("runBatch", String.class, String.class, String.class,
                        GlobalDriftPolicyImpactAutomationController.RunBatch.class);
        Method claim = GlobalDriftPolicyImpactAutomationController.class.getMethod("claimRunnable",String.class,
                String.class,GlobalDriftPolicyImpactAutomationController.ClaimRunnable.class);

        assertEquals("/internal/v1/global-drift-policy-impact-jobs/runnable",
                base + runnable.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("/internal/v1/global-drift-policy-impact-jobs/{jobId}:run-batch",
                base + batch.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("/internal/v1/global-drift-policy-impact-jobs:claim-runnable",
                base + claim.getAnnotation(PostMapping.class).value()[0]);
        assertTrue(java.util.Arrays.stream(batch.getParameterAnnotations()[1])
                .anyMatch(annotation -> annotation instanceof RequestHeader header
                        && "Authorization".equals(header.value())));
    }
}
