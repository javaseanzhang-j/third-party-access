package com.ftk.tpip.mapping.execution;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.*;
import com.ftk.tpip.mapping.api.*;
import com.ftk.tpip.mapping.compiler.DefaultMappingCompiler;
import com.ftk.tpip.shared.AssetCode;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefaultMappingEngineTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultMappingCompiler compiler = new DefaultMappingCompiler();
    private final DefaultMappingEngine engine = new DefaultMappingEngine(objectMapper);
    private final MappingContext context = new MappingContext("req-1", "trace-1", "refund.create", Map.of("tenant", "fashion"));

    @Test void readsNestedSourceAndBuildsObjectsAndArrays() throws Exception {
        var plan = plan(List.of(rule("amount", 10, ValueSource.SELECTOR, "$.data.items[0].price",
                "$.order.lines[0].amount", "NUMBER", null, null, true, "TO_NUMBER", null,
                MissingStrategy.FAIL, ErrorStrategy.FAIL)));
        JsonNode source = objectMapper.readTree("{\"data\":{\"items\":[{\"price\":\"12.50\"}]}}");
        var result = engine.transform(plan, source, context);
        assertTrue(result.successful());
        assertEquals("12.50", result.output().at("/order/lines/0/amount").decimalValue().toPlainString());
        assertEquals("12.50", source.at("/data/items/0/price").textValue());
    }

    @Test void appliesDefaultEnumAndContextValues() throws Exception {
        var status = rule("status", 10, ValueSource.SELECTOR, "$.state", "$.status", "STRING",
                null, "\"UNKNOWN\"", false, "ENUM", "{\"values\":{\"S\":\"SUCCESS\"},\"default\":\"OTHER\"}",
                MissingStrategy.DEFAULT, ErrorStrategy.FAIL);
        var trace = rule("trace", 20, ValueSource.CONTEXT, "traceId", "$.metadata.traceId", "STRING",
                null, null, true, null, null, MissingStrategy.FAIL, ErrorStrategy.FAIL);
        var result = engine.transform(plan(List.of(status, trace)), objectMapper.readTree("{}"), context);
        assertTrue(result.successful());
        assertEquals("OTHER", result.output().get("status").textValue());
        assertEquals("trace-1", result.output().at("/metadata/traceId").textValue());
    }

    @Test void reportsMissingRequiredValueWithoutThrowing() throws Exception {
        var result = engine.transform(plan(List.of(rule("id", 1, ValueSource.SELECTOR, "$.id", "$.id",
                "STRING", null, null, true, null, null, MissingStrategy.FAIL, ErrorStrategy.FAIL))),
                objectMapper.readTree("{}"), context);
        assertFalse(result.successful());
        assertEquals("MAPPING_SOURCE_MISSING", result.diagnostics().getFirst().code());
    }

    @Test void ignoredConversionFailureProducesWarningAndContinues() throws Exception {
        var invalid = rule("amount", 1, ValueSource.SELECTOR, "$.amount", "$.amount", "NUMBER",
                null, null, false, "TO_NUMBER", null, MissingStrategy.IGNORE, ErrorStrategy.IGNORE);
        var constant = rule("source", 2, ValueSource.CONSTANT, null, "$.source", "STRING",
                "\"TPIP\"", null, true, null, null, MissingStrategy.FAIL, ErrorStrategy.FAIL);
        var result = engine.transform(plan(List.of(invalid, constant)), objectMapper.readTree("{\"amount\":\"not-number\"}"), context);
        assertTrue(result.successful());
        assertEquals(DiagnosticSeverity.WARNING, result.diagnostics().getFirst().severity());
        assertEquals("TPIP", result.output().get("source").textValue());
    }

    private com.ftk.tpip.mapping.ir.CompiledMappingPlan plan(List<MappingRule> rules) {
        return compiler.compile(new MappingSpecification(AssetCode.of("fixture.mapping"), 1,
                MappingDirection.OUTBOUND_REQUEST, rules));
    }
    private static MappingRule rule(String code, int order, ValueSource sourceKind, String source,
            String target, String targetType, String constant, String defaultValue, boolean required,
            String converter, String converterConfig, MissingStrategy missing, ErrorStrategy error) {
        return new MappingRule(code, order, sourceKind, source, target, targetType, constant, defaultValue,
                required, converter, converterConfig, null, null, missing, error, true);
    }
}
