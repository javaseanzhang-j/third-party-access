package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class VerificationDriftWorkbenchControllerContractTest {
    @Test
    void exposesDedicatedReadOnlyWorkbenchResources() {
        RequestMapping root = VerificationDriftWorkbenchController.class.getAnnotation(RequestMapping.class);
        assertArrayEquals(new String[] {"/control/v1/verification-drift-workbench"}, root.value());
        assertArrayEquals(new String[] {"/reports"}, get("reports", 9).value());
        assertArrayEquals(new String[] {"/summary"}, get("summary", 7).value());
        assertArrayEquals(new String[] {"/groups"}, get("groups", 8).value());
        assertArrayEquals(new String[] {"/governance-evaluations"}, get("governanceEvaluations", 3).value());
        assertArrayEquals(new String[] {"/governance-operations/{commandKey}"}, get("operation", 1).value());
        assertArrayEquals(new String[] {"/governance-metrics"}, get("governanceMetrics", 3).value());
        assertArrayEquals(new String[] {"/governance-policy-impact"}, get("governancePolicyImpact", 5).value());
        assertArrayEquals(new String[] {"/governance-policy-impact-snapshots/{snapshotId}"},
                get("policyImpactSnapshot", 1).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-snapshots/{snapshotId}"},
                get("globalPolicyImpactSnapshot", 1).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-snapshots/{snapshotId}/workspace-snapshots"},
                get("globalPolicyImpactWorkspaceSnapshots", 3).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-jobs/{jobId}"},
                get("globalPolicyImpactJob", 1).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-jobs/{jobId}/items"},
                get("globalPolicyImpactJobItems", 3).value());
        assertArrayEquals(new String[] {"/governance-reviews:assign"}, post("assign", 3).value());
        assertArrayEquals(new String[] {"/governance-reviews:acknowledge"}, post("acknowledge", 3).value());
        assertArrayEquals(new String[] {"/governance-reviews:dispose"}, post("dispose", 3).value());
        assertArrayEquals(new String[] {"/governance-policy-impact-snapshots"},
                post("createPolicyImpactSnapshot", 2).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-snapshots"},
                post("createGlobalPolicyImpactSnapshot", 2).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-jobs"},
                post("createGlobalPolicyImpactJob", 2).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-jobs/{jobId}:run-batch"},
                post("runGlobalPolicyImpactJobBatch", 3).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-jobs/{jobId}:retry-failed"},
                post("retryGlobalPolicyImpactJob", 3).value());
        assertArrayEquals(new String[] {"/global-governance-policy-impact-jobs/{jobId}:seal"},
                post("sealGlobalPolicyImpactJob", 3).value());
    }

    private static GetMapping get(String name, int parameterCount) {
        return java.util.Arrays.stream(VerificationDriftWorkbenchController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount)
                .findFirst().orElseThrow().getAnnotation(GetMapping.class);
    }

    private static PostMapping post(String name, int parameterCount) {
        return java.util.Arrays.stream(VerificationDriftWorkbenchController.class.getDeclaredMethods())
                .filter(method -> method.getName().equals(name) && method.getParameterCount() == parameterCount)
                .findFirst().orElseThrow().getAnnotation(PostMapping.class);
    }
}
