package com.ftk.tpip.control.application.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.mapping.api.MappingDiagnostic;
import java.util.List;

public record MappingFixtureResult(boolean successful, JsonNode output,
        List<MappingDiagnostic> diagnostics, String compiledPlanChecksum) {}
