package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class GlobalImpactJobQueryControllerContractTest {
    @Test
    void exposesDedicatedUiReadModelWithoutChangingCommandRoutes() {
        String base = GlobalImpactJobQueryController.class.getAnnotation(RequestMapping.class).value()[0];
        assertEquals("/control/v1/verification-drift-workbench/global-governance-policy-impact-job-views", base);
        assertEquals("", route("jobs"));
        assertEquals("/{jobId}", route("job"));
        assertEquals("/{jobId}/workspace-impacts", route("workspaceImpacts"));
        assertEquals("/{jobId}/impact-summary", route("workspaceImpactSummary"));
        assertEquals("/{jobId}/timeline", route("timeline"));
    }

    private static String route(String name) {
        for (Method method : GlobalImpactJobQueryController.class.getDeclaredMethods()) {
            if (method.getName().equals(name)) {
                String[] values = method.getAnnotation(GetMapping.class).value();
                return values.length == 0 ? "" : values[0];
            }
        }
        throw new AssertionError("Missing route " + name);
    }
}
