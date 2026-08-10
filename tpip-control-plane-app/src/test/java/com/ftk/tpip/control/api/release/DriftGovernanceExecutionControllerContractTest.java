package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DriftGovernanceExecutionControllerContractTest {
    @Test
    void exposesExplicitMaterializationAndReadOnlyLedgerResources() throws Exception {
        assertArrayEquals(new String[] {"/control/v1"},
                DriftGovernanceExecutionController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-executions:materialize"},
                DriftGovernanceExecutionController.class.getMethod("materialize",
                        DriftGovernanceExecutionController.MaterializeRequest.class, String.class)
                        .getAnnotation(PostMapping.class).value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-executions"},
                DriftGovernanceExecutionController.class.getMethod("list", long.class)
                        .getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-executions:due"},
                DriftGovernanceExecutionController.class.getMethod("due", long.class, int.class)
                        .getAnnotation(GetMapping.class).value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-executions/{executionId}"},
                DriftGovernanceExecutionController.class.getMethod("get", long.class)
                        .getAnnotation(GetMapping.class).value());
    }
}
