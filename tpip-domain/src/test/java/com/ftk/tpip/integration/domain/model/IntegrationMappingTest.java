package com.ftk.tpip.integration.domain.model;

import static org.junit.jupiter.api.Assertions.*;
import com.ftk.tpip.shared.AssetCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class IntegrationMappingTest {
    @Test void stableIdentityIsPreservedWhenRevised() {
        var saved = new IntegrationMapping(1L, 2, AssetCode.of("refund.request.mapping"), "Refund",
                MappingAssetDirection.OUTBOUND_REQUEST, MappingStatus.ACTIVE, 3, null, null);
        var revised = saved.revise("Refund v2", MappingStatus.INACTIVE, 3);
        assertEquals(saved.mappingCode(), revised.mappingCode());
        assertEquals(saved.bindingId(), revised.bindingId());
        assertEquals(saved.direction(), revised.direction());
    }

    @Test void mappingVersionRequiresRules() {
        assertThrows(IllegalArgumentException.class, () -> IntegrationMappingVersion.draft(1,
                SelectorProfile.JSONPATH_1_0, null, null, "{}", "a".repeat(64), List.of()));
    }
}
