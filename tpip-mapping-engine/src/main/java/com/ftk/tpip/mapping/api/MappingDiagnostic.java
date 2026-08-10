package com.ftk.tpip.mapping.api;

import java.util.Objects;

public record MappingDiagnostic(String ruleCode, String code, String message, DiagnosticSeverity severity) {
    public MappingDiagnostic {
        code = Objects.requireNonNull(code, "code must not be null");
        message = Objects.requireNonNull(message, "message must not be null");
        severity = Objects.requireNonNull(severity, "severity must not be null");
    }
    public MappingDiagnostic(String ruleCode, String code, String message) {
        this(ruleCode, code, message, DiagnosticSeverity.ERROR);
    }
}
