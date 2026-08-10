package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class DriftGovernanceReminderObservabilityControllerContractTest {
    @Test
    void exposesReadOnlyDiffDeliveryTimelineAndMetricsResources() {
        assertArrayEquals(new String[] {"/control/v1/verification-drift-workbench"},
                DriftGovernanceReminderObservabilityController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[] {"/governance-reminder-batches/{batchId}/diff"}, get("diff").value());
        assertArrayEquals(new String[] {"/governance-reminder-batches/{batchId}/delivery-status"},
                get("delivery").value());
        assertArrayEquals(new String[] {"/governance-reminder-batches/{batchId}/timeline"},
                get("timeline").value());
        assertArrayEquals(new String[] {"/governance-reminder-metrics"}, get("metrics").value());
        assertEquals(0, Arrays.stream(DriftGovernanceReminderObservabilityController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class)).count());
    }
    private static GetMapping get(String name) { return method(name).getAnnotation(GetMapping.class); }
    private static Method method(String name) {
        return Arrays.stream(DriftGovernanceReminderObservabilityController.class.getDeclaredMethods())
                .filter(value -> value.getName().equals(name)).findFirst().orElseThrow();
    }
}
