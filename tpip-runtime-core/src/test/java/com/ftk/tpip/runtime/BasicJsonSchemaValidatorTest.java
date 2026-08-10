package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BasicJsonSchemaValidatorTest {
    private final ObjectMapper json = new ObjectMapper();
    private final BasicJsonSchemaValidator validator = new BasicJsonSchemaValidator();

    @Test
    void validatesRequiredTypesAndAdditionalProperties() throws Exception {
        var schema = json.readTree("""
                {"type":"object","required":["id"],"properties":{"id":{"type":"string","minLength":2}},"additionalProperties":false}
                """);
        assertTrue(validator.validate(schema, json.readTree("{\"id\":\"A1\"}")).valid());
        var invalid = validator.validate(schema, json.readTree("{\"id\":1,\"extra\":true}"));
        assertFalse(invalid.valid());
        assertTrue(invalid.violations().stream().anyMatch(value -> value.contains("expected type")));
        assertTrue(invalid.violations().stream().anyMatch(value -> value.contains("additional property")));
    }

    @Test
    void failsClosedForUnsupportedAssertionKeyword() throws Exception {
        var result = validator.validate(json.readTree("{\"type\":\"object\",\"oneOf\":[]}"), json.readTree("{}"));
        assertFalse(result.valid());
        assertTrue(result.violations().getFirst().contains("unsupported assertion keyword"));
    }
}
