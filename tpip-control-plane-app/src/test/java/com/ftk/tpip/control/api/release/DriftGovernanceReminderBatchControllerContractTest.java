package com.ftk.tpip.control.api.release;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.*;

class DriftGovernanceReminderBatchControllerContractTest {
    @Test
    void exposesCreateApproveDispatchAndReadResources() {
        assertArrayEquals(new String[] {"/control/v1"},
                DriftGovernanceReminderBatchController.class.getAnnotation(RequestMapping.class).value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-reminder-batches"},
                post("create").value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-reminder-batches/{batchId}:approve"},
                post("approve").value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-reminder-batches/{batchId}:cancel"},
                post("cancel").value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-reminder-batches/{batchId}:replace"},
                post("replace").value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-reminder-batches/{batchId}:dispatch"},
                post("dispatch").value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-reminder-batches"},
                get("list").value());
        assertArrayEquals(new String[] {"/verification-drift-workbench/governance-reminder-batches/{batchId}"},
                get("get").value());
    }
    private static PostMapping post(String name) { return method(name).getAnnotation(PostMapping.class); }
    private static GetMapping get(String name) { return method(name).getAnnotation(GetMapping.class); }
    private static Method method(String name) {
        return java.util.Arrays.stream(DriftGovernanceReminderBatchController.class.getDeclaredMethods())
                .filter(value -> value.getName().equals(name)).findFirst().orElseThrow();
    }
}
