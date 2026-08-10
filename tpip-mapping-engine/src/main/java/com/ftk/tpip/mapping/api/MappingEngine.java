package com.ftk.tpip.mapping.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;

public interface MappingEngine {

    MappingResult transform(CompiledMappingPlan plan, JsonNode source, MappingContext context);
}
