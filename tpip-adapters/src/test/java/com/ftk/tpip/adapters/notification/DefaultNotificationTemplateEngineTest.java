package com.ftk.tpip.adapters.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DefaultNotificationTemplateEngineTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DefaultNotificationTemplateEngine engine = new DefaultNotificationTemplateEngine(json);

    @Test
    void compilesAndRendersTypedVariables() throws Exception {
        String template = "{\"severity\":\"{{payload.severity}}\",\"event\":\"{{eventType}}\",\"attempts\":\"{{payload.attempts}}\"}";
        String schema = "{\"type\":\"object\",\"properties\":{"
                + "\"eventType\":{\"type\":\"string\"},\"payload.severity\":{\"type\":\"string\"},"
                + "\"payload.attempts\":{\"type\":\"integer\"}},"
                + "\"required\":[\"eventType\",\"payload.severity\",\"payload.attempts\"]}";

        var compiled = engine.compile(template, schema);
        var result = json.readTree(engine.render(compiled.templateDocument(), compiled.variableSchema(),
                "{\"eventType\":\"ALERT_OPENED\",\"payload\":{\"severity\":\"WARNING\",\"attempts\":3}}"));

        assertEquals("ALERT_OPENED", result.path("event").asText());
        assertEquals("WARNING", result.path("severity").asText());
        assertEquals(3, result.path("attempts").asInt());
        assertEquals("[\"eventType\",\"payload.attempts\",\"payload.severity\"]", compiled.referencedVariables());
    }

    @Test
    void rejectsUndeclaredAndTypeMismatchedVariables() {
        String schema = "{\"type\":\"object\",\"properties\":{\"payload.count\":{\"type\":\"integer\"}},\"required\":[\"payload.count\"]}";
        assertThrows(IllegalArgumentException.class,
                () -> engine.compile("{\"value\":\"{{payload.unknown}}\"}", schema));
        assertThrows(IllegalArgumentException.class,
                () -> engine.render("{\"value\":\"{{payload.count}}\"}", schema,
                        "{\"payload\":{\"count\":\"three\"}}"));
    }
}
