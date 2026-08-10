package com.ftk.tpip.adapters.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ftk.tpip.release.domain.model.CompiledNotificationTemplate;
import com.ftk.tpip.release.domain.service.NotificationTemplateEngine;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DefaultNotificationTemplateEngine implements NotificationTemplateEngine {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z][A-Za-z0-9_]*)*)}}");
    private static final Set<String> METADATA = Set.of("eventId", "eventType", "aggregateType",
            "aggregateId", "environmentCode");
    private static final Set<String> TYPES = Set.of("string", "number", "integer", "boolean", "object", "array");
    private final ObjectMapper json;

    public DefaultNotificationTemplateEngine(ObjectMapper json) { this.json = json; }

    @Override
    public CompiledNotificationTemplate compile(String templateDocument, String variableSchema) {
        JsonNode template = parse(templateDocument, "templateDocument");
        JsonNode schema = parse(variableSchema, "variableSchema");
        if (!template.isObject()) throw invalid("templateDocument must be a JSON object");
        Schema contract = schema(schema);
        LinkedHashSet<String> referenced = new LinkedHashSet<>();
        scan(template, referenced);
        for (String variable : referenced) {
            if (!allowed(variable)) throw invalid("template variable is not allowed: " + variable);
            if (!contract.properties().containsKey(variable)) {
                throw invalid("template variable is not declared: " + variable);
            }
            if (!contract.required().contains(variable)) {
                throw invalid("template variable must be required: " + variable);
            }
        }
        ArrayNode variables = json.createArrayNode();
        referenced.stream().sorted().forEach(variables::add);
        return new CompiledNotificationTemplate(write(canonical(template)), write(canonical(schema)),
                write(variables));
    }

    @Override
    public String render(String templateDocument, String variableSchema, String contextDocument) {
        CompiledNotificationTemplate compiled = compile(templateDocument, variableSchema);
        JsonNode context = parse(contextDocument, "contextDocument");
        if (!context.isObject()) throw invalid("contextDocument must be a JSON object");
        Schema contract = schema(parse(compiled.variableSchema(), "variableSchema"));
        for (String variable : contract.required()) {
            JsonNode value = resolve(context, variable);
            if (value == null || value.isMissingNode() || value.isNull()) {
                throw invalid("required template variable is missing: " + variable);
            }
            if (!matches(value, contract.properties().get(variable))) {
                throw invalid("template variable type does not match schema: " + variable);
            }
        }
        return write(canonical(renderNode(parse(compiled.templateDocument(), "templateDocument"), context)));
    }

    private Schema schema(JsonNode schema) {
        if (!schema.isObject() || !"object".equals(schema.path("type").asText())) {
            throw invalid("variableSchema must describe an object");
        }
        JsonNode properties = schema.get("properties");
        JsonNode required = schema.get("required");
        if (properties == null || !properties.isObject() || required == null || !required.isArray()) {
            throw invalid("variableSchema properties and required are mandatory");
        }
        Map<String, String> types = new TreeMap<>();
        properties.fields().forEachRemaining(entry -> {
            String type = entry.getValue().path("type").asText();
            if (!TYPES.contains(type)) throw invalid("unsupported variable type: " + entry.getKey());
            if (!allowed(entry.getKey())) throw invalid("schema variable is not allowed: " + entry.getKey());
            types.put(entry.getKey(), type);
        });
        LinkedHashSet<String> mandatory = new LinkedHashSet<>();
        for (JsonNode item : required) {
            if (!item.isTextual() || !mandatory.add(item.textValue()) || !types.containsKey(item.textValue())) {
                throw invalid("variableSchema required contains an invalid variable");
            }
        }
        return new Schema(types, mandatory);
    }

    private void scan(JsonNode node, Set<String> variables) {
        if (node.isTextual()) {
            String text = node.textValue();
            Matcher matcher = PLACEHOLDER.matcher(text);
            String stripped = matcher.replaceAll("");
            matcher.reset();
            while (matcher.find()) variables.add(matcher.group(1));
            if (stripped.contains("{{") || stripped.contains("}}")) throw invalid("template contains malformed placeholder");
        } else if (node.isContainerNode()) {
            node.forEach(child -> scan(child, variables));
        }
    }

    private JsonNode renderNode(JsonNode node, JsonNode context) {
        if (node.isTextual()) {
            Matcher exact = PLACEHOLDER.matcher(node.textValue());
            if (exact.matches()) return require(context, exact.group(1)).deepCopy();
            Matcher matcher = PLACEHOLDER.matcher(node.textValue());
            StringBuffer value = new StringBuffer();
            while (matcher.find()) {
                JsonNode replacement = require(context, matcher.group(1));
                if (replacement.isContainerNode()) throw invalid("object or array variable cannot be embedded in text: " + matcher.group(1));
                matcher.appendReplacement(value, Matcher.quoteReplacement(replacement.asText()));
            }
            matcher.appendTail(value);
            return json.getNodeFactory().textNode(value.toString());
        }
        if (node.isArray()) {
            ArrayNode result = json.createArrayNode();
            node.forEach(child -> result.add(renderNode(child, context)));
            return result;
        }
        if (node.isObject()) {
            ObjectNode result = json.createObjectNode();
            node.fields().forEachRemaining(entry -> result.set(entry.getKey(), renderNode(entry.getValue(), context)));
            return result;
        }
        return node.deepCopy();
    }

    private JsonNode require(JsonNode context, String variable) {
        JsonNode value = resolve(context, variable);
        if (value == null || value.isMissingNode() || value.isNull()) throw invalid("template variable is missing: " + variable);
        return value;
    }

    private static JsonNode resolve(JsonNode context, String variable) {
        JsonNode current = context;
        for (String segment : variable.split("\\.")) current = current == null ? null : current.get(segment);
        return current;
    }

    private static boolean matches(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "number" -> value.isNumber();
            case "integer" -> value.isIntegralNumber();
            case "boolean" -> value.isBoolean();
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            default -> false;
        };
    }

    private static boolean allowed(String variable) {
        return METADATA.contains(variable) || variable.matches("payload(?:\\.[A-Za-z][A-Za-z0-9_]*)+");
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = json.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            node.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((key, value) -> result.set(key, canonical(value)));
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = json.createArrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        return node.deepCopy();
    }

    private JsonNode parse(String value, String field) {
        try { return json.readTree(value); }
        catch (JsonProcessingException exception) { throw invalid(field + " must be valid JSON"); }
    }

    private String write(JsonNode value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Could not serialize template", exception); }
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private record Schema(Map<String, String> properties, Set<String> required) {}
}
