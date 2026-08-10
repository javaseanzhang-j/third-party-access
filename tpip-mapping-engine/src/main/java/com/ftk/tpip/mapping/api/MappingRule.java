package com.ftk.tpip.mapping.api;

import java.util.Objects;

public record MappingRule(
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
        String conditionExpression,
        ArrayStrategy arrayStrategy,
        MissingStrategy missingStrategy,
        ErrorStrategy errorStrategy,
        boolean enabled) {

    public MappingRule {
        ruleCode = required(ruleCode, "ruleCode");
        if (order < 0) throw new IllegalArgumentException("order must not be negative");
        valueSource = Objects.requireNonNull(valueSource, "valueSource must not be null");
        targetSelector = required(targetSelector, "targetSelector");
        missingStrategy = Objects.requireNonNull(missingStrategy, "missingStrategy must not be null");
        errorStrategy = Objects.requireNonNull(errorStrategy, "errorStrategy must not be null");
    }

    public MappingRule(String ruleCode, int order, String sourceSelector, String targetSelector,
            String targetType, boolean required, String converterCode,
            java.util.Map<String, Object> ignoredLegacyConfig) {
        this(ruleCode, order, ValueSource.SELECTOR, sourceSelector, targetSelector, targetType,
                null, null, required, converterCode, null, null, null,
                required ? MissingStrategy.FAIL : MissingStrategy.IGNORE, ErrorStrategy.FAIL, true);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
