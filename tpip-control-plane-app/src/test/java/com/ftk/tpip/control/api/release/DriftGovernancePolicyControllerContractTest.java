package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DriftGovernancePolicyControllerContractTest {
    @Test
    void exposesVersionedLifecycleAndResolutionEndpoint() throws Exception {
        assertArrayEquals(new String[] {"/control/v1"},
                DriftGovernancePolicyController.class.getAnnotation(RequestMapping.class).value());
        GetMapping resolve = DriftGovernancePolicyController.class.getDeclaredMethod("resolve", long.class)
                .getAnnotation(GetMapping.class);
        assertArrayEquals(new String[] {"/drift-governance-policies:resolve"}, resolve.value());
    }
}
