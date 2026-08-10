package com.ftk.tpip.shared;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AssetCodeTest {

    @Test
    void acceptsCanonicalOperationCode() {
        assertEquals("customer.identity.verify", AssetCode.of("customer.identity.verify").value());
    }

    @Test
    void rejectsProviderSpecificDisplayName() {
        assertThrows(IllegalArgumentException.class, () -> AssetCode.of("Provider A Verify"));
    }
}
