package com.ftk.tpip.policy.api;

import java.util.List;

public class PolicyCompilationException extends RuntimeException {
    private final List<PolicyDiagnostic> diagnostics;
    public PolicyCompilationException(List<PolicyDiagnostic> diagnostics) {
        super("Policy compilation failed with " + diagnostics.size() + " diagnostic(s)");
        this.diagnostics = List.copyOf(diagnostics);
    }
    public List<PolicyDiagnostic> diagnostics() { return diagnostics; }
}
