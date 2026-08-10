package com.ftk.tpip.mapping.compiler;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.mapping.api.*;
import com.ftk.tpip.shared.AssetCode;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefaultMappingCompilerTest {
    private final DefaultMappingCompiler compiler = new DefaultMappingCompiler();

    @Test void compilesAndOrdersDeterministicRules() {
        var later = rule("name", 20, "$.payload.user_name", "$.customer.name", true);
        var first = rule("id", 10, "$.payload.user_id", "$.customer.id", true);
        var plan = compiler.compile(new MappingSpecification(AssetCode.of("provider.customer.response"),
                1, MappingDirection.INBOUND_RESPONSE, List.of(later, first)));
        assertEquals(List.of("id", "name"), plan.rules().stream().map(r -> r.ruleCode()).toList());
        assertEquals(64, plan.checksum().length());
    }

    @Test void rejectsRecursiveOrFilterJsonPath() {
        var bad = rule("bad", 1, "$..password", "$.customer.password", false);
        var ex = assertThrows(MappingCompilationException.class, () -> compiler.compile(
                new MappingSpecification(AssetCode.of("provider.customer.response"), 1,
                        MappingDirection.INBOUND_RESPONSE, List.of(bad))));
        assertEquals("MAPPING_INVALID_SOURCE_SELECTOR", ex.diagnostics().getFirst().code());
    }

    @Test void rejectsEnumConverterWithoutStaticDictionary() {
        var bad = new MappingRule("status", 1, ValueSource.SELECTOR, "$.status", "$.status",
                "STRING", null, null, true, "ENUM", "{}", null, null,
                MissingStrategy.FAIL, ErrorStrategy.FAIL, true);
        var ex = assertThrows(MappingCompilationException.class, () -> compiler.compile(
                new MappingSpecification(AssetCode.of("provider.customer.response"), 1,
                        MappingDirection.INBOUND_RESPONSE, List.of(bad))));
        assertEquals("MAPPING_INVALID_ENUM_CONFIG", ex.diagnostics().getFirst().code());
    }

    private static MappingRule rule(String code, int order, String source, String target, boolean required) {
        return new MappingRule(code, order, ValueSource.SELECTOR, source, target, "STRING", null,
                null, required, null, null, null, null, required ? MissingStrategy.FAIL : MissingStrategy.IGNORE,
                ErrorStrategy.FAIL, true);
    }
}
