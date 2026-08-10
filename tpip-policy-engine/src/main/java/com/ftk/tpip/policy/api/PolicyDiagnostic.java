package com.ftk.tpip.policy.api;

import java.util.Objects;

public record PolicyDiagnostic(String stage, String stepId, String code, String message,
        PolicyDiagnosticSeverity severity) {
    public PolicyDiagnostic {
        code = Objects.requireNonNull(code); message = Objects.requireNonNull(message);
        severity = Objects.requireNonNull(severity);
    }
    public static PolicyDiagnostic error(String stage, String stepId, String code, String message) {
        return new PolicyDiagnostic(stage, stepId, code, message, PolicyDiagnosticSeverity.ERROR);
    }
}
