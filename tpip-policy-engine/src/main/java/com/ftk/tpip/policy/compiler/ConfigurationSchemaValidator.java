package com.ftk.tpip.policy.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

final class ConfigurationSchemaValidator {
    private static final Set<String> SECRET_SCHEMES = Set.of("vault", "secret", "env", "aws-secretsmanager",
            "azure-keyvault", "gcp-secretmanager", "local-secret");
    private static final Set<String> SCHEMA_KEYS = Set.of("type", "properties", "required", "additionalProperties",
            "items", "enum", "pattern", "format", "minimum", "maximum", "minItems", "maxItems", "description");
    private ConfigurationSchemaValidator() {}
    static void validateSchema(JsonNode schema) {
        if (!schema.isObject()) throw new IllegalArgumentException("configurationSchema must be an object");
        schema.fieldNames().forEachRemaining(key -> { if (!SCHEMA_KEYS.contains(key)) throw new IllegalArgumentException("Unsupported configuration schema keyword: " + key); });
        JsonNode type = schema.get("type");
        if (type == null || !type.isTextual()) throw new IllegalArgumentException("Each configuration schema node requires type");
        if (schema.has("properties")) schema.get("properties").fields().forEachRemaining(entry -> validateSchema(entry.getValue()));
        if (schema.has("items")) validateSchema(schema.get("items"));
        if (schema.path("additionalProperties").isObject()) validateSchema(schema.get("additionalProperties"));
        if (schema.has("pattern")) Pattern.compile(schema.get("pattern").asText());
    }
    static List<String> validate(JsonNode schema, JsonNode value) {
        List<String> errors = new ArrayList<>(); validate(schema, value, "$", errors); return errors;
    }
    private static void validate(JsonNode schema, JsonNode value, String path, List<String> errors) {
        String type = schema.path("type").asText();
        boolean matches = switch (type) {
            case "object" -> value != null && value.isObject(); case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual(); case "integer" -> value != null && value.isIntegralNumber();
            case "number" -> value != null && value.isNumber(); case "boolean" -> value != null && value.isBoolean(); default -> false;
        };
        if (!matches) { errors.add(path + " must be " + type); return; }
        if (schema.has("enum")) {
            boolean found = false; for (JsonNode candidate : schema.get("enum")) if (candidate.equals(value)) found = true;
            if (!found) errors.add(path + " is not an allowed value");
        }
        if (value.isObject()) validateObject(schema, value, path, errors);
        if (value.isArray()) validateArray(schema, value, path, errors);
        if (value.isTextual()) validateString(schema, value.asText(), path, errors);
        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.get("minimum").decimalValue()) < 0) errors.add(path + " is below minimum");
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.get("maximum").decimalValue()) > 0) errors.add(path + " exceeds maximum");
        }
    }
    private static void validateObject(JsonNode schema, JsonNode value, String path, List<String> errors) {
        Set<String> required = new HashSet<>(); if (schema.has("required")) schema.get("required").forEach(item -> required.add(item.asText()));
        required.forEach(name -> { if (!value.has(name)) errors.add(path + "." + name + " is required"); });
        JsonNode properties = schema.path("properties");
        value.fields().forEachRemaining(entry -> {
            JsonNode childSchema = properties.get(entry.getKey());
            if (childSchema != null) validate(childSchema, entry.getValue(), path + "." + entry.getKey(), errors);
            else if (schema.path("additionalProperties").isBoolean() && !schema.path("additionalProperties").asBoolean()) errors.add(path + "." + entry.getKey() + " is not allowed");
            else if (schema.path("additionalProperties").isObject()) validate(schema.path("additionalProperties"), entry.getValue(), path + "." + entry.getKey(), errors);
        });
    }
    private static void validateArray(JsonNode schema, JsonNode value, String path, List<String> errors) {
        if (schema.has("minItems") && value.size() < schema.get("minItems").asInt()) errors.add(path + " has too few items");
        if (schema.has("maxItems") && value.size() > schema.get("maxItems").asInt()) errors.add(path + " has too many items");
        if (schema.has("items")) for (int i=0;i<value.size();i++) validate(schema.get("items"), value.get(i), path + "[" + i + "]", errors);
    }
    private static void validateString(JsonNode schema, String value, String path, List<String> errors) {
        if (schema.has("pattern") && !Pattern.compile(schema.get("pattern").asText()).matcher(value).matches()) errors.add(path + " does not match required pattern");
        if ("secret-ref".equals(schema.path("format").asText())) {
            try { URI uri = URI.create(value); if (uri.getScheme()==null || !SECRET_SCHEMES.contains(uri.getScheme().toLowerCase(Locale.ROOT))
                    || uri.getRawSchemeSpecificPart()==null || uri.getRawSchemeSpecificPart().isBlank()
                    || uri.getQuery()!=null || uri.getFragment()!=null || uri.getRawUserInfo()!=null)
                errors.add(path + " must be a supported Secret Reference without user-info, query or fragment"); }
            catch (Exception exception) { errors.add(path + " must be a valid secret reference"); }
        }
    }
}
