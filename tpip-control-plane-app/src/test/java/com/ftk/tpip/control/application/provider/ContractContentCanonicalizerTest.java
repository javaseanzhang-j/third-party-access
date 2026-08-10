package com.ftk.tpip.control.application.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ContractContentCanonicalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CanonicalJsonService canonicalJson = new CanonicalJsonService(objectMapper);
    private final ContractContentCanonicalizer canonicalizer =
            new ContractContentCanonicalizer(objectMapper, canonicalJson);

    @Test
    void producesSameChecksumForDifferentObjectFieldOrder() throws Exception {
        var first = objectMapper.readTree("""
                {"type":"object","properties":{"orderId":{"type":"string"},"amount":{"type":"number"}}}
                """);
        var second = objectMapper.readTree("""
                {"properties":{"amount":{"type":"number"},"orderId":{"type":"string"}},"type":"object"}
                """);

        CanonicalContractContent firstContent = canonicalizer.canonicalize(first, null, null, null, null);
        CanonicalContractContent secondContent = canonicalizer.canonicalize(second, null, null, null, null);

        assertEquals(firstContent.requestSchema(), secondContent.requestSchema());
        assertEquals(firstContent.checksum(), secondContent.checksum());
    }

    @Test
    void rejectsNonObjectSchema() throws Exception {
        var array = objectMapper.readTree("[1,2,3]");
        assertThrows(
                IllegalArgumentException.class,
                () -> canonicalizer.canonicalize(array, null, null, null, null));
    }
}
