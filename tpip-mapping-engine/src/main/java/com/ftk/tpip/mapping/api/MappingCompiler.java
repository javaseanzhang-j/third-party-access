package com.ftk.tpip.mapping.api;

import com.ftk.tpip.mapping.ir.CompiledMappingPlan;

public interface MappingCompiler {

    CompiledMappingPlan compile(MappingSpecification specification);
}
