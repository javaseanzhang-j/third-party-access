package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Runtime-safe validation profile for the structural JSON Schema keywords used by TPIP contracts.
 * Unsupported annotation keywords are ignored; unsupported assertion keywords fail closed.
 */
public final class BasicJsonSchemaValidator implements ContractValidator {
    private static final Set<String> SUPPORTED = Set.of(
            "$schema", "$id", "title", "description", "default", "examples", "deprecated", "readOnly", "writeOnly",
            "type", "required", "properties", "items", "enum", "const", "additionalProperties",
            "minLength", "maxLength", "minimum", "maximum", "minItems", "maxItems", "pattern");

    @Override
    public ContractValidationResult validate(JsonNode schema, JsonNode instance) {
        Objects.requireNonNull(schema, "schema must not be null");
        Objects.requireNonNull(instance, "instance must not be null");
        List<String> violations = new ArrayList<>();
        validateSchemaKeywords(schema, "$", violations);
        validateNode(schema, instance, "$", violations);
        return new ContractValidationResult(violations);
    }

    public ContractValidationResult validateSchema(JsonNode schema) {
        Objects.requireNonNull(schema, "schema must not be null");
        List<String> violations = new ArrayList<>();
        validateSchemaKeywords(schema, "$", violations);
        return new ContractValidationResult(violations);
    }

    private void validateSchemaKeywords(JsonNode schema, String path, List<String> violations) {
        if (schema.isBoolean()) return;
        if (!schema.isObject()) {
            violations.add(path + ": schema must be an object or boolean");
            return;
        }
        schema.fieldNames().forEachRemaining(keyword -> {
            if (!SUPPORTED.contains(keyword)) violations.add(path + ": unsupported assertion keyword " + keyword);
        });
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            properties.fields().forEachRemaining(entry -> validateSchemaKeywords(entry.getValue(), path + "." + entry.getKey(), violations));
        }
        JsonNode items = schema.get("items");
        if (items != null) validateSchemaKeywords(items, path + "[]", violations);
        JsonNode additional = schema.get("additionalProperties");
        if (additional != null && additional.isObject()) validateSchemaKeywords(additional, path + ".*", violations);
    }

    private void validateNode(JsonNode schema, JsonNode value, String path, List<String> violations) {
        if (schema.isBoolean()) {
            if (!schema.booleanValue()) violations.add(path + ": value is rejected by schema");
            return;
        }
        if (!schema.isObject()) return;
        if (!matchesType(schema.get("type"), value)) {
            violations.add(path + ": expected type " + schema.path("type") + " but was " + nodeType(value));
            return;
        }
        JsonNode enumeration = schema.get("enum");
        if (enumeration != null && enumeration.isArray() && !contains(enumeration, value)) {
            violations.add(path + ": value is not in enum");
        }
        JsonNode constant = schema.get("const");
        if (constant != null && !constant.equals(value)) violations.add(path + ": value does not match const");
        if (value.isObject()) validateObject(schema, value, path, violations);
        if (value.isArray()) validateArray(schema, value, path, violations);
        if (value.isTextual()) validateString(schema, value.textValue(), path, violations);
        if (value.isNumber()) validateNumber(schema, value.decimalValue(), path, violations);
    }

    private void validateObject(JsonNode schema, JsonNode value, String path, List<String> violations) {
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) required.forEach(name -> {
            if (name.isTextual() && !value.has(name.textValue())) violations.add(path + ": missing required property " + name.textValue());
        });
        JsonNode properties = schema.get("properties");
        Set<String> known = new HashSet<>();
        if (properties != null && properties.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                known.add(entry.getKey());
                JsonNode child = value.get(entry.getKey());
                if (child != null) validateNode(entry.getValue(), child, path + "." + entry.getKey(), violations);
            });
        }
        JsonNode additional = schema.get("additionalProperties");
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (known.contains(field.getKey())) continue;
            if (additional != null && additional.isBoolean() && !additional.booleanValue()) {
                violations.add(path + ": additional property is not allowed: " + field.getKey());
            } else if (additional != null && additional.isObject()) {
                validateNode(additional, field.getValue(), path + "." + field.getKey(), violations);
            }
        }
    }

    private void validateArray(JsonNode schema, JsonNode value, String path, List<String> violations) {
        compareSize(schema, "minItems", value.size(), path, true, violations);
        compareSize(schema, "maxItems", value.size(), path, false, violations);
        JsonNode items = schema.get("items");
        if (items != null) for (int index = 0; index < value.size(); index++) {
            validateNode(items, value.get(index), path + "[" + index + "]", violations);
        }
    }

    private void validateString(JsonNode schema, String value, String path, List<String> violations) {
        compareSize(schema, "minLength", value.codePointCount(0, value.length()), path, true, violations);
        compareSize(schema, "maxLength", value.codePointCount(0, value.length()), path, false, violations);
        JsonNode pattern = schema.get("pattern");
        if (pattern != null && pattern.isTextual()) {
            try {
                if (!Pattern.compile(pattern.textValue()).matcher(value).find()) {
                    violations.add(path + ": string does not match pattern");
                }
            } catch (PatternSyntaxException exception) {
                violations.add(path + ": schema contains an invalid pattern");
            }
        }
    }

    private void validateNumber(JsonNode schema, BigDecimal value, String path, List<String> violations) {
        JsonNode minimum = schema.get("minimum");
        if (minimum != null && minimum.isNumber() && value.compareTo(minimum.decimalValue()) < 0) violations.add(path + ": number is below minimum");
        JsonNode maximum = schema.get("maximum");
        if (maximum != null && maximum.isNumber() && value.compareTo(maximum.decimalValue()) > 0) violations.add(path + ": number is above maximum");
    }

    private static void compareSize(JsonNode schema, String keyword, int actual, String path, boolean minimum,
            List<String> violations) {
        JsonNode limit = schema.get(keyword);
        if (limit == null || !limit.canConvertToInt()) return;
        if ((minimum && actual < limit.intValue()) || (!minimum && actual > limit.intValue())) {
            violations.add(path + ": size violates " + keyword + "=" + limit.intValue());
        }
    }

    private static boolean matchesType(JsonNode type, JsonNode value) {
        if (type == null) return true;
        if (type.isArray()) {
            for (JsonNode candidate : type) if (candidate.isTextual() && matchesType(candidate.textValue(), value)) return true;
            return false;
        }
        return type.isTextual() && matchesType(type.textValue(), value);
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "null" -> value.isNull();
            default -> false;
        };
    }

    private static boolean contains(JsonNode array, JsonNode value) {
        for (JsonNode candidate : array) if (candidate.equals(value)) return true;
        return false;
    }

    private static String nodeType(JsonNode value) {
        return value.getNodeType().name().toLowerCase();
    }
}
