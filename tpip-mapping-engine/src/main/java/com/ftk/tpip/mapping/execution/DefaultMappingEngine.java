package com.ftk.tpip.mapping.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.ftk.tpip.mapping.api.*;
import com.ftk.tpip.mapping.ir.*;
import com.ftk.tpip.mapping.selector.DeterministicJsonPath;
import java.math.BigDecimal;
import java.util.*;

public final class DefaultMappingEngine implements MappingEngine {
    private final ObjectMapper objectMapper;
    public DefaultMappingEngine(ObjectMapper objectMapper) { this.objectMapper = Objects.requireNonNull(objectMapper); }

    @Override public MappingResult transform(CompiledMappingPlan plan, JsonNode source, MappingContext context) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(context, "context must not be null");
        JsonNode output = MissingNode.getInstance();
        List<MappingDiagnostic> diagnostics = new ArrayList<>();
        for (CompiledMappingRule rule : plan.rules()) {
            try {
                JsonNode value = resolve(rule, source, context);
                if (missing(value)) {
                    value = onMissing(rule, diagnostics);
                    if (missing(value)) continue;
                }
                value = transformValue(rule, value);
                output = JsonTargetWriter.write(output.deepCopy(), DeterministicJsonPath.parse(rule.targetSelector()), value);
            } catch (RuntimeException exception) {
                output = onError(rule, output, diagnostics, exception);
            }
        }
        if (output.isMissingNode()) output = objectMapper.createObjectNode();
        return new MappingResult(output, diagnostics);
    }

    private JsonNode resolve(CompiledMappingRule rule, JsonNode source, MappingContext context) {
        return switch (rule.valueSource()) {
            case SELECTOR -> DeterministicJsonPath.parse(rule.sourceSelector()).read(source);
            case CONSTANT -> parse(rule.constantValue(), "constantValue");
            case CONTEXT -> contextValue(rule.sourceSelector(), context);
        };
    }
    private JsonNode contextValue(String key, MappingContext context) {
        Object value = switch (key) {
            case "requestId" -> context.requestId();
            case "traceId" -> context.traceId();
            case "operationCode" -> context.operationCode();
            default -> context.attributes().get(key.startsWith("attributes.") ? key.substring(11) : key);
        };
        return value == null ? MissingNode.getInstance() : objectMapper.valueToTree(value);
    }
    private JsonNode onMissing(CompiledMappingRule rule, List<MappingDiagnostic> diagnostics) {
        return switch (rule.missingStrategy()) {
            case IGNORE -> MissingNode.getInstance();
            case DEFAULT -> parse(rule.defaultValue(), "defaultValue");
            case FAIL -> {
                diagnostics.add(new MappingDiagnostic(rule.ruleCode(), "MAPPING_SOURCE_MISSING",
                        "Required source value is missing", DiagnosticSeverity.ERROR));
                yield MissingNode.getInstance();
            }
        };
    }
    private JsonNode onError(CompiledMappingRule rule, JsonNode output,
            List<MappingDiagnostic> diagnostics, RuntimeException failure) {
        if (rule.errorStrategy() == ErrorStrategy.IGNORE) {
            diagnostics.add(new MappingDiagnostic(rule.ruleCode(), "MAPPING_RULE_ERROR_IGNORED",
                    safeMessage(failure), DiagnosticSeverity.WARNING));
            return output;
        }
        if (rule.errorStrategy() == ErrorStrategy.USE_DEFAULT && rule.defaultValue() != null) {
            try {
                JsonNode fallback = transformValue(rule, parse(rule.defaultValue(), "defaultValue"));
                return JsonTargetWriter.write(output.deepCopy(), DeterministicJsonPath.parse(rule.targetSelector()), fallback);
            } catch (RuntimeException fallbackFailure) {
                failure = fallbackFailure;
            }
        }
        diagnostics.add(new MappingDiagnostic(rule.ruleCode(), "MAPPING_RULE_EXECUTION_FAILED",
                safeMessage(failure), DiagnosticSeverity.ERROR));
        return output;
    }
    private JsonNode transformValue(CompiledMappingRule rule, JsonNode value) {
        if (rule.arrayStrategy() == ArrayStrategy.FIRST && value.isArray()) {
            value = value.isEmpty() ? MissingNode.getInstance() : value.get(0);
        } else if (rule.arrayStrategy() == ArrayStrategy.EACH) {
            if (!value.isArray()) throw new IllegalArgumentException("EACH array strategy requires an array source");
            ArrayNode transformed = objectMapper.createArrayNode();
            for (JsonNode item : value) transformed.add(coerce(convert(rule, item), rule.targetType()));
            return transformed;
        }
        return coerce(convert(rule, value), rule.targetType());
    }
    private JsonNode convert(CompiledMappingRule rule, JsonNode value) {
        String converter = rule.converterCode();
        if (converter == null || "IDENTITY".equals(converter)) return value.deepCopy();
        return switch (converter) {
            case "TO_STRING" -> toStringNode(value);
            case "TO_NUMBER" -> toNumber(value);
            case "TO_BOOLEAN" -> toBoolean(value);
            case "ENUM" -> enumValue(value, rule.converterConfig());
            default -> throw new IllegalArgumentException("Unsupported converter: " + converter);
        };
    }
    private JsonNode coerce(JsonNode value, String type) {
        if (type == null) return value;
        return switch (type) {
            case "STRING" -> toStringNode(value);
            case "NUMBER" -> toNumber(value);
            case "BOOLEAN" -> toBoolean(value);
            case "OBJECT" -> { if (!value.isObject()) throw new IllegalArgumentException("Value is not an object"); yield value; }
            case "ARRAY" -> { if (!value.isArray()) throw new IllegalArgumentException("Value is not an array"); yield value; }
            default -> throw new IllegalArgumentException("Unsupported target type: " + type);
        };
    }
    private JsonNode enumValue(JsonNode value, String config) {
        JsonNode document = parse(config, "converterConfig");
        JsonNode values = document.get("values");
        if (values == null || !values.isObject()) throw new IllegalArgumentException("ENUM converter requires converterConfig.values object");
        JsonNode mapped = values.get(value.asText());
        if (mapped == null) mapped = document.get("default");
        if (mapped == null) throw new IllegalArgumentException("ENUM converter has no mapping for " + value.asText());
        return mapped.deepCopy();
    }
    private static JsonNode toStringNode(JsonNode value) {
        if (value.isTextual()) return value.deepCopy();
        if (value.isValueNode() && !value.isNull()) return TextNode.valueOf(value.asText());
        throw new IllegalArgumentException("Object or array cannot be converted to STRING");
    }
    private static JsonNode toNumber(JsonNode value) {
        if (value.isNumber()) return value.deepCopy();
        if (!value.isTextual()) throw new IllegalArgumentException("Value cannot be converted to NUMBER");
        try { return DecimalNode.valueOf(new BigDecimal(value.textValue())); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Invalid number: " + value.textValue()); }
    }
    private static JsonNode toBoolean(JsonNode value) {
        if (value.isBoolean()) return value.deepCopy();
        if (value.isTextual()) {
            if ("true".equalsIgnoreCase(value.textValue())) return BooleanNode.TRUE;
            if ("false".equalsIgnoreCase(value.textValue())) return BooleanNode.FALSE;
        }
        if (value.isIntegralNumber() && value.longValue() == 1) return BooleanNode.TRUE;
        if (value.isIntegralNumber() && value.longValue() == 0) return BooleanNode.FALSE;
        throw new IllegalArgumentException("Value cannot be converted to BOOLEAN");
    }
    private JsonNode parse(String value, String field) {
        if (value == null) return MissingNode.getInstance();
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException(field + " is invalid JSON", e); }
    }
    private static boolean missing(JsonNode value) { return value == null || value.isMissingNode() || value.isNull(); }
    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage(); return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
