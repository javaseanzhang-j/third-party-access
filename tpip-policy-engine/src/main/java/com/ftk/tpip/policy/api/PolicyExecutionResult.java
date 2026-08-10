package com.ftk.tpip.policy.api;

import java.util.List;

public record PolicyExecutionResult(boolean success, List<String> diagnostics) {

    public PolicyExecutionResult {
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public static PolicyExecutionResult succeeded() {
        return new PolicyExecutionResult(true, List.of());
    }
}
