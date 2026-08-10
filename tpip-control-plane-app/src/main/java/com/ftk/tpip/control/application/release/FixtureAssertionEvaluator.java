package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.ftk.tpip.mapping.selector.DeterministicJsonPath;
import com.ftk.tpip.runtime.BasicJsonSchemaValidator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Executes the bounded Fixture Assertion Profile 1.0. It never performs I/O or executes arbitrary code. */
@Component
public final class FixtureAssertionEvaluator {
    private static final Pattern CODE = Pattern.compile("^[A-Za-z][A-Za-z0-9._-]{0,99}$");
    private static final Pattern HEADER = Pattern.compile("^[!#$%&'*+.^_`|~0-9A-Za-z-]{1,100}$");
    private static final Pattern POLICY = Pattern.compile("^(success|http\\.status|header\\.[!#$%&'*+.^_`|~0-9A-Za-z-]+|\\$[^\\s]*)\\s*(==|!=)\\s*(.+)$");
    private static final Set<String> JSON_PATH_OPERATORS = Set.of(
            "EXISTS", "NOT_EXISTS", "EQUALS", "NOT_EQUALS", "CONTAINS", "MATCHES");

    private final ObjectMapper json;
    private final BasicJsonSchemaValidator schemas = new BasicJsonSchemaValidator();

    public FixtureAssertionEvaluator(ObjectMapper json) {
        this.json = Objects.requireNonNull(json);
    }

    public JsonNode validateAndCanonicalize(JsonNode document) {
        if (document == null || document.isNull()) return null;
        if (!document.isArray() || document.isEmpty()) {
            throw new IllegalArgumentException("assertions must be a non-empty JSON array");
        }
        if (document.size() > 100) throw new IllegalArgumentException("a FixtureCase supports at most 100 assertions");
        Set<String> codes = new HashSet<>();
        document.forEach(assertion -> validateAssertion(assertion, codes));
        return document.deepCopy();
    }

    public JsonNode validateForMapping(JsonNode document) {
        JsonNode validated = validateAndCanonicalize(document);
        if (validated == null) return null;
        validated.forEach(assertion -> {
            AssertionType assertionType = type(assertion);
            if (assertionType == AssertionType.HTTP_STATUS || assertionType == AssertionType.HTTP_HEADER) {
                throw new IllegalArgumentException(assertionType + " is only valid for a REMOTE_CALL Fixture");
            }
            if (assertionType == AssertionType.POLICY_EXPRESSION) {
                String selector = parsePolicy(assertion.path("expression").textValue()).selector();
                if (selector.equals("http.status") || selector.startsWith("header.")) {
                    throw new IllegalArgumentException("HTTP Policy selectors are only valid for a REMOTE_CALL Fixture");
                }
            }
        });
        return validated;
    }

    public EvaluationReport evaluate(JsonNode document, AssertionContext context) {
        JsonNode assertions = validateAndCanonicalize(document);
        Objects.requireNonNull(context, "context must not be null");
        List<AssertionResult> results = new ArrayList<>();
        assertions.forEach(assertion -> results.add(evaluateAssertion(assertion, context)));
        return new EvaluationReport(results);
    }

    private void validateAssertion(JsonNode assertion, Set<String> codes) {
        if (!assertion.isObject()) throw new IllegalArgumentException("each assertion must be a JSON object");
        String code = text(assertion, "code");
        if (!CODE.matcher(code).matches()) throw new IllegalArgumentException("assertion code is invalid: " + code);
        if (!codes.add(code)) throw new IllegalArgumentException("assertion code is duplicated: " + code);
        AssertionType type = type(assertion);
        switch (type) {
            case SUCCESS -> requireBoolean(assertion, "expected");
            case DIAGNOSTIC_CODE -> text(assertion, "expected");
            case JSON_PATH -> {
                DeterministicJsonPath.parse(text(assertion, "path"));
                String operator = operator(assertion);
                if (!Set.of("EXISTS", "NOT_EXISTS").contains(operator) && !assertion.has("expected")) {
                    throw new IllegalArgumentException("JSON_PATH " + operator + " requires expected");
                }
                if ("MATCHES".equals(operator)) compilePattern(assertion.path("expected"));
            }
            case JSON_SCHEMA -> {
                JsonNode schema = assertion.get("schema");
                if (schema == null) throw new IllegalArgumentException("JSON_SCHEMA requires schema");
                var validation = schemas.validateSchema(schema);
                if (!validation.valid()) throw new IllegalArgumentException("invalid JSON Schema: " + validation.violations());
            }
            case POLICY_EXPRESSION -> parsePolicy(text(assertion, "expression"));
            case HTTP_STATUS -> {
                int expected = requireInt(assertion, "expected");
                if (expected < 100 || expected > 599) throw new IllegalArgumentException("HTTP_STATUS expected must be between 100 and 599");
            }
            case HTTP_HEADER -> {
                String name = text(assertion, "name");
                if (!HEADER.matcher(name).matches()) throw new IllegalArgumentException("HTTP_HEADER name is invalid");
                String operator = operator(assertion);
                if (!Set.of("EXISTS", "NOT_EXISTS").contains(operator) && !assertion.has("expected")) {
                    throw new IllegalArgumentException("HTTP_HEADER " + operator + " requires expected");
                }
                if ("MATCHES".equals(operator)) compilePattern(assertion.path("expected"));
            }
        }
    }

    private AssertionResult evaluateAssertion(JsonNode assertion, AssertionContext context) {
        String code = assertion.path("code").textValue();
        AssertionType type = type(assertion);
        return switch (type) {
            case SUCCESS -> result(code, type, context.successful() == assertion.path("expected").booleanValue(),
                    BooleanNode.valueOf(context.successful()), "mapping success state");
            case DIAGNOSTIC_CODE -> {
                String expected = assertion.path("expected").textValue();
                boolean passed = context.diagnosticCodes().contains(expected);
                yield result(code, type, passed, json.valueToTree(context.diagnosticCodes()), "diagnostic code");
            }
            case JSON_PATH -> compare(code, type, operator(assertion),
                    DeterministicJsonPath.parse(assertion.path("path").textValue()).read(context.body()), assertion.get("expected"));
            case JSON_SCHEMA -> {
                var validation = schemas.validate(assertion.path("schema"), context.body());
                yield result(code, type, validation.valid(), json.valueToTree(validation.violations()), "JSON Schema");
            }
            case POLICY_EXPRESSION -> evaluatePolicy(code, assertion.path("expression").textValue(), context);
            case HTTP_STATUS -> {
                requireHttpContext(context, type);
                yield result(code, type, context.httpStatus() == assertion.path("expected").intValue(),
                        IntNode.valueOf(context.httpStatus()), "HTTP status");
            }
            case HTTP_HEADER -> {
                requireHttpContext(context, type);
                JsonNode actual = header(context.headers(), assertion.path("name").textValue());
                yield compare(code, type, operator(assertion), actual, assertion.get("expected"));
            }
        };
    }

    private AssertionResult evaluatePolicy(String code, String expression, AssertionContext context) {
        PolicyExpression policy = parsePolicy(expression);
        JsonNode actual = switch (policy.selector()) {
            case "success" -> BooleanNode.valueOf(context.successful());
            case "http.status" -> {
                requireHttpContext(context, AssertionType.POLICY_EXPRESSION);
                yield IntNode.valueOf(context.httpStatus());
            }
            default -> {
                if (policy.selector().startsWith("header.")) {
                    requireHttpContext(context, AssertionType.POLICY_EXPRESSION);
                    yield header(context.headers(), policy.selector().substring(7));
                }
                yield DeterministicJsonPath.parse(policy.selector()).read(context.body());
            }
        };
        boolean exists = !actual.isMissingNode();
        boolean equal = exists && actual.equals(policy.expected());
        boolean passed = exists && ("==".equals(policy.operator()) ? equal : !equal);
        return result(code, AssertionType.POLICY_EXPRESSION, passed, actual, "Policy expression");
    }

    private AssertionResult compare(String code, AssertionType type, String operator, JsonNode actual, JsonNode expected) {
        boolean exists = actual != null && !actual.isMissingNode();
        boolean passed = switch (operator) {
            case "EXISTS" -> exists;
            case "NOT_EXISTS" -> !exists;
            case "EQUALS" -> exists && actual.equals(expected);
            case "NOT_EQUALS" -> exists && !actual.equals(expected);
            case "CONTAINS" -> exists && contains(actual, expected);
            case "MATCHES" -> exists && actual.isTextual()
                    && Pattern.compile(expected.textValue()).matcher(actual.textValue()).find();
            default -> throw new IllegalArgumentException("unsupported assertion operator " + operator);
        };
        return result(code, type, passed, actual == null ? MissingNode.getInstance() : actual, operator);
    }

    private static boolean contains(JsonNode actual, JsonNode expected) {
        if (actual.isTextual() && expected != null && expected.isTextual()) return actual.textValue().contains(expected.textValue());
        if (actual.isArray()) for (JsonNode item : actual) if (item.equals(expected)) return true;
        return actual.isObject() && expected != null && expected.isTextual() && actual.has(expected.textValue());
    }

    private AssertionResult result(String code, AssertionType type, boolean passed, JsonNode actual, String message) {
        return new AssertionResult(code, type, passed, actual == null ? NullNode.getInstance() : actual, message);
    }

    private PolicyExpression parsePolicy(String expression) {
        if (expression.length() > 1000 || expression.contains(";") || expression.contains("${")) {
            throw new IllegalArgumentException("Policy expression is outside the safe profile");
        }
        Matcher matcher = POLICY.matcher(expression.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("Policy expression is outside the safe profile");
        String selector = matcher.group(1);
        if (selector.startsWith("$")) DeterministicJsonPath.parse(selector);
        JsonNode expected;
        try {
            expected = json.readTree(matcher.group(3));
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Policy expression right operand must be a JSON scalar", failure);
        }
        if (expected == null || !expected.isValueNode()) throw new IllegalArgumentException("Policy expression right operand must be a JSON scalar");
        return new PolicyExpression(selector, matcher.group(2), expected);
    }

    private JsonNode header(Map<String, List<String>> headers, String name) {
        for (var entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) {
                if (entry.getValue() == null || entry.getValue().isEmpty()) return TextNode.valueOf("");
                if (entry.getValue().size() == 1) return TextNode.valueOf(entry.getValue().getFirst());
                return json.valueToTree(entry.getValue());
            }
        }
        return MissingNode.getInstance();
    }

    private static void requireHttpContext(AssertionContext context, AssertionType type) {
        if (context.httpStatus() == null) throw new IllegalArgumentException(type + " requires a REMOTE_CALL HTTP context");
    }

    private static String operator(JsonNode assertion) {
        String value = assertion.path("operator").asText("EQUALS").toUpperCase(Locale.ROOT);
        if (!JSON_PATH_OPERATORS.contains(value)) throw new IllegalArgumentException("unsupported assertion operator " + value);
        return value;
    }

    private static AssertionType type(JsonNode assertion) {
        try {
            return AssertionType.valueOf(text(assertion, "type").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("unsupported assertion type: " + assertion.path("type").asText(), failure);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.textValue().trim();
    }

    private static void requireBoolean(JsonNode node, String field) {
        if (!node.path(field).isBoolean()) throw new IllegalArgumentException(field + " must be boolean");
    }

    private static int requireInt(JsonNode node, String field) {
        if (!node.path(field).isIntegralNumber() || !node.path(field).canConvertToInt()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return node.path(field).intValue();
    }

    private static void compilePattern(JsonNode value) {
        if (!value.isTextual() || value.textValue().length() > 1000) {
            throw new IllegalArgumentException("MATCHES expected must be a string of at most 1000 characters");
        }
        String expression = value.textValue();
        if (expression.indexOf('(') >= 0 || expression.indexOf(')') >= 0 || expression.indexOf('|') >= 0
                || expression.matches(".*\\\\[1-9].*") || expression.contains(".*.*")) {
            throw new IllegalArgumentException("MATCHES expected is outside the bounded regular expression profile");
        }
        try {
            Pattern.compile(expression);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("MATCHES expected contains an invalid regular expression", failure);
        }
    }

    public enum AssertionType {
        SUCCESS, DIAGNOSTIC_CODE, JSON_PATH, JSON_SCHEMA, POLICY_EXPRESSION, HTTP_STATUS, HTTP_HEADER
    }

    public record AssertionContext(boolean successful, JsonNode body, Set<String> diagnosticCodes,
            Integer httpStatus, Map<String, List<String>> headers) {
        public AssertionContext {
            body = body == null ? NullNode.getInstance() : body;
            diagnosticCodes = diagnosticCodes == null ? Set.of() : Set.copyOf(diagnosticCodes);
            headers = headers == null ? Map.of() : Map.copyOf(headers);
        }

        public static AssertionContext mapping(boolean successful, JsonNode output, Set<String> diagnostics) {
            return new AssertionContext(successful, output, diagnostics, null, Map.of());
        }
    }

    public record AssertionResult(String code, AssertionType type, boolean passed, JsonNode actual, String message) {}

    public record EvaluationReport(List<AssertionResult> results) {
        public EvaluationReport { results = List.copyOf(results); }
        public boolean passed() { return results.stream().allMatch(AssertionResult::passed); }
    }

    private record PolicyExpression(String selector, String operator, JsonNode expected) {}
}
