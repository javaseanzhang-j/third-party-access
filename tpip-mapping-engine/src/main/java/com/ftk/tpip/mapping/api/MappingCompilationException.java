package com.ftk.tpip.mapping.api;

import java.util.List;

public class MappingCompilationException extends RuntimeException {
    private final List<MappingDiagnostic> diagnostics;
    public MappingCompilationException(List<MappingDiagnostic> diagnostics) {
        super("Mapping compilation failed with " + diagnostics.size() + " diagnostic(s)");
        this.diagnostics = List.copyOf(diagnostics);
    }
    public List<MappingDiagnostic> diagnostics() { return diagnostics; }
}
