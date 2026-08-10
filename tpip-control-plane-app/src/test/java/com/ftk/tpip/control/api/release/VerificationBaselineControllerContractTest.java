package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

class VerificationBaselineControllerContractTest {
    @Test
    void baselineCommandOnlyReferencesServerVerificationRun() {
        String[] components = Arrays.stream(VerificationBaselineController.CreateBaseline.class.getRecordComponents())
                .map(component -> component.getName()).toArray(String[]::new);
        assertArrayEquals(new String[] {"sourceVerificationRunId"}, components);
    }

    @Test
    void exposesExplicitManualRegressionEndpoint() throws Exception {
        PostMapping mapping = VerificationBaselineController.class
                .getDeclaredMethod("run", long.class, String.class).getAnnotation(PostMapping.class);
        assertArrayEquals(new String[] {"/verification-baselines/{baselineId}:run"}, mapping.value());
    }

    @Test
    void exposesExplicitDriftDecisionEndpoints() throws Exception {
        PostMapping acknowledge = VerificationBaselineController.class.getDeclaredMethod("acknowledge", long.class,
                VerificationBaselineController.DecisionCommand.class, String.class).getAnnotation(PostMapping.class);
        PostMapping accept = VerificationBaselineController.class.getDeclaredMethod("accept", long.class,
                VerificationBaselineController.DecisionCommand.class, String.class).getAnnotation(PostMapping.class);
        assertArrayEquals(new String[] {"/verification-drift-reports/{reportId}:acknowledge"}, acknowledge.value());
        assertArrayEquals(new String[] {"/verification-drift-reports/{reportId}:accept"}, accept.value());
    }

    @Test
    void exposesReadOnlyLineageTrendAndImpactEndpoints() throws Exception {
        assertArrayEquals(new String[] {"/verification-baselines/{baselineId}/lineage"},
                get("lineage").value());
        assertArrayEquals(new String[] {"/verification-baselines/{baselineId}/drift-trend"},
                get("driftTrend").value());
        assertArrayEquals(new String[] {"/verification-baselines/{baselineId}/impact"},
                get("impact").value());
    }

    private static GetMapping get(String method) throws Exception {
        return VerificationBaselineController.class.getDeclaredMethod(method, long.class).getAnnotation(GetMapping.class);
    }
}
