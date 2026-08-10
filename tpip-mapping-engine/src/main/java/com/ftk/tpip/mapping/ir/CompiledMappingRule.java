package com.ftk.tpip.mapping.ir;

import com.ftk.tpip.mapping.api.*;

public record CompiledMappingRule(
        String ruleCode,
        int order,
        ValueSource valueSource,
        String sourceSelector,
        String targetSelector,
        String targetType,
        String constantValue,
        String defaultValue,
        boolean required,
        String converterCode,
        String converterConfig,
        ArrayStrategy arrayStrategy,
        MissingStrategy missingStrategy,
        ErrorStrategy errorStrategy) {
}
