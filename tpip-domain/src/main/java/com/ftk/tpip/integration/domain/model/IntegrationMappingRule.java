package com.ftk.tpip.integration.domain.model;

import java.util.Objects;

public record IntegrationMappingRule(Long id, Long parentRuleId, String ruleCode, int ruleOrder,
        MappingValueSource valueSource, String sourceSelector, String targetSelector,
        MappingTargetType targetType, String constantValue, String defaultValue, String converterCode,
        String converterConfig, String conditionExpression, boolean required,
        MappingArrayStrategy arrayStrategy, MappingMissingStrategy missingStrategy,
        MappingErrorStrategy errorStrategy, boolean enabled) {
    public IntegrationMappingRule {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (parentRuleId != null && parentRuleId <= 0) throw new IllegalArgumentException("parentRuleId must be positive");
        ruleCode = required(ruleCode, "ruleCode", 180);
        if (ruleOrder < 0) throw new IllegalArgumentException("ruleOrder must not be negative");
        valueSource = Objects.requireNonNull(valueSource, "valueSource must not be null");
        targetSelector = required(targetSelector, "targetSelector", 1000);
        missingStrategy = Objects.requireNonNull(missingStrategy, "missingStrategy must not be null");
        errorStrategy = Objects.requireNonNull(errorStrategy, "errorStrategy must not be null");
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        if (normalized.length() > max) throw new IllegalArgumentException(field + " must not exceed " + max + " characters");
        return normalized;
    }
}
