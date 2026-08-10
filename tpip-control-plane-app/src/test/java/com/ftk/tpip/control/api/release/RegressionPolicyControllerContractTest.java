package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;

class RegressionPolicyControllerContractTest {
    @Test
    void policyVersionCommandContainsBoundedScheduleControls() {
        String[] fields = Arrays.stream(RegressionPolicyController.CreateVersion.class.getRecordComponents())
                .map(component -> component.getName()).toArray(String[]::new);
        assertArrayEquals(new String[] {"baselineId", "intervalSeconds", "failureBackoffSeconds",
                "maximumConsecutiveFailures"}, fields);
    }

    @Test
    void activationIsExplicitAndControllerHasNoSchedulerToggle() throws Exception {
        PostMapping mapping = RegressionPolicyController.class.getDeclaredMethod("activate", long.class,
                RegressionPolicyController.ActivatePolicy.class, String.class).getAnnotation(PostMapping.class);
        assertArrayEquals(new String[] {"/{policyId}:activate"}, mapping.value());
        assertFalse(Arrays.stream(RegressionPolicyController.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("enableScheduler")));
    }
}
