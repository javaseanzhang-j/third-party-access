package com.ftk.tpip.mapping.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;

public record MappingResult(JsonNode output, List<MappingDiagnostic> diagnostics) {

    public MappingResult {
        output = Objects.requireNonNull(output, "output must not be null");
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }

    public boolean successful() {
        return diagnostics.stream().noneMatch(diagnostic -> diagnostic.severity() == DiagnosticSeverity.ERROR);
    }
}
