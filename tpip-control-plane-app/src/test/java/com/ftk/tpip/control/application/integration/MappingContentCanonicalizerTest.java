package com.ftk.tpip.control.application.integration;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.integration.domain.model.*;
import com.ftk.tpip.mapping.api.MappingCompilationException;
import com.ftk.tpip.mapping.compiler.DefaultMappingCompiler;
import java.util.List;
import org.junit.jupiter.api.Test;

class MappingContentCanonicalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MappingContentCanonicalizer canonicalizer = new MappingContentCanonicalizer(
            new CanonicalJsonService(objectMapper), new DefaultMappingCompiler());

    @Test void checksumIsStableAcrossJsonObjectFieldOrder() throws Exception {
        var a = canonicalizer.canonicalize("provider.refund.response", MappingAssetDirection.INBOUND_RESPONSE,
                SelectorProfile.JSONPATH_1_0, objectMapper.readTree("{\"b\":2,\"a\":1}"), List.of(rule("$.data.refund_no")));
        var b = canonicalizer.canonicalize("provider.refund.response", MappingAssetDirection.INBOUND_RESPONSE,
                SelectorProfile.JSONPATH_1_0, objectMapper.readTree("{\"a\":1,\"b\":2}"), List.of(rule("$.data.refund_no")));
        assertEquals(a.checksum(), b.checksum());
    }

    @Test void invalidJsonPathReturnsCompilerDiagnostics() {
        var exception = assertThrows(MappingCompilationException.class, () -> canonicalizer.canonicalize(
                "provider.refund.response", MappingAssetDirection.INBOUND_RESPONSE,
                SelectorProfile.JSONPATH_1_0, null, List.of(rule("$..refund_no"))));
        assertEquals("MAPPING_INVALID_SOURCE_SELECTOR", exception.diagnostics().getFirst().code());
    }

    private static MappingRuleInput rule(String source) {
        return new MappingRuleInput("refund.id", 10, MappingValueSource.SELECTOR, source, "$.refundId",
                MappingTargetType.STRING, null, null, null, null, null, true, null,
                MappingMissingStrategy.FAIL, MappingErrorStrategy.FAIL, true);
    }
}
