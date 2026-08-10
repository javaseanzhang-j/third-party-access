package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class FixtureAssertionEvaluatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final FixtureAssertionEvaluator evaluator = new FixtureAssertionEvaluator(json);

    @Test
    void evaluatesMappingAssertionProfile() throws Exception {
        JsonNode assertions = json.readTree("""
                [
                  {"code":"success","type":"SUCCESS","expected":true},
                  {"code":"customer-id","type":"JSON_PATH","path":"$.customer.id","operator":"EQUALS","expected":"C1001"},
                  {"code":"mobile","type":"JSON_PATH","path":"$.mobile","operator":"MATCHES","expected":"^138[0-9]{8}$"},
                  {"code":"contract","type":"JSON_SCHEMA","schema":{"type":"object","required":["customer"],"properties":{"customer":{"type":"object","required":["id"]}}}},
                  {"code":"business-rule","type":"POLICY_EXPRESSION","expression":"$.status == \\\"ACTIVE\\\""}
                ]
                """);
        JsonNode body = json.readTree("{\"customer\":{\"id\":\"C1001\"},\"mobile\":\"13800138000\",\"status\":\"ACTIVE\"}");

        var report = evaluator.evaluate(assertions,
                FixtureAssertionEvaluator.AssertionContext.mapping(true, body, Set.of()));

        assertTrue(report.passed());
        assertEquals(5, report.results().size());
    }

    @Test
    void reportsEveryFailedAssertionWithoutShortCircuiting() throws Exception {
        JsonNode assertions = json.readTree("""
                [
                  {"code":"success","type":"SUCCESS","expected":true},
                  {"code":"code","type":"DIAGNOSTIC_CODE","expected":"MAP_REQUIRED_SOURCE_MISSING"},
                  {"code":"missing","type":"JSON_PATH","path":"$.missing","operator":"EXISTS"},
                  {"code":"missing-not-equal","type":"JSON_PATH","path":"$.missing","operator":"NOT_EQUALS","expected":"value"}
                ]
                """);

        var report = evaluator.evaluate(assertions,
                FixtureAssertionEvaluator.AssertionContext.mapping(false, json.createObjectNode(), Set.of("OTHER")));

        assertFalse(report.passed());
        assertEquals(4, report.results().stream().filter(result -> !result.passed()).count());
    }

    @Test
    void evaluatesHttpAssertionsOnlyWithExplicitHttpContext() throws Exception {
        JsonNode assertions = json.readTree("""
                [
                  {"code":"status","type":"HTTP_STATUS","expected":200},
                  {"code":"trace","type":"HTTP_HEADER","name":"X-Trace-Id","operator":"EQUALS","expected":"t-1"},
                  {"code":"policy","type":"POLICY_EXPRESSION","expression":"http.status == 200"}
                ]
                """);
        var context = new FixtureAssertionEvaluator.AssertionContext(true, json.createObjectNode(), Set.of(), 200,
                Map.of("x-trace-id", List.of("t-1")));

        assertTrue(evaluator.evaluate(assertions, context).passed());
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateForMapping(assertions));
    }

    @Test
    void rejectsUnsafeOrNonDeterministicDefinitionsAtCreationTime() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateAndCanonicalize(json.readTree("""
                [{"code":"duplicate","type":"SUCCESS","expected":true},{"code":"duplicate","type":"SUCCESS","expected":true}]
                """)));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateAndCanonicalize(json.readTree("""
                [{"code":"path","type":"JSON_PATH","path":"$..secret","operator":"EXISTS"}]
                """)));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateAndCanonicalize(json.readTree("""
                [{"code":"schema","type":"JSON_SCHEMA","schema":{"oneOf":[]}}]
                """)));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateAndCanonicalize(json.readTree("""
                [{"code":"policy","type":"POLICY_EXPRESSION","expression":"$.status == \\\"OK\\\"; drop"}]
                """)));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateAndCanonicalize(json.readTree("""
                [{"code":"regex","type":"JSON_PATH","path":"$.name","operator":"MATCHES","expected":"["}]
                """)));
        assertThrows(IllegalArgumentException.class, () -> evaluator.validateAndCanonicalize(json.readTree("""
                [{"code":"regex","type":"JSON_PATH","path":"$.name","operator":"MATCHES","expected":"(a+)+"}]
                """)));
    }
}
