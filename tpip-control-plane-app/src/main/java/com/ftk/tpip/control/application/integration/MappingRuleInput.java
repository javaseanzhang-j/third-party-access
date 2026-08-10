package com.ftk.tpip.control.application.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.integration.domain.model.*;

public record MappingRuleInput(String ruleCode, int ruleOrder, MappingValueSource valueSource,
        String sourceSelector, String targetSelector, MappingTargetType targetType,
        JsonNode constantValue, JsonNode defaultValue, String converterCode, JsonNode converterConfig,
        String conditionExpression, boolean required, MappingArrayStrategy arrayStrategy,
        MappingMissingStrategy missingStrategy, MappingErrorStrategy errorStrategy, boolean enabled) {}
