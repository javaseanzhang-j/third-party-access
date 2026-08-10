package com.ftk.tpip.mapping.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingSpecificationTest {

    @Test
    void createsVersionedSpecification() {
        MappingRule rule = new MappingRule(
                "customer-name", 10, "$.data.name", "$.customer.name",
                "STRING", true, null, Map.of());

        MappingSpecification specification = new MappingSpecification(
                AssetCode.of("provider-a.identity.response"),
                1,
                MappingDirection.INBOUND_RESPONSE,
                List.of(rule));

        assertEquals(1, specification.rules().size());
    }

    @Test
    void rejectsNonPositiveVersion() {
        assertThrows(IllegalArgumentException.class, () -> new MappingSpecification(
                AssetCode.of("provider-a.identity.response"),
                0,
                MappingDirection.INBOUND_RESPONSE,
                List.of()));
    }
}
