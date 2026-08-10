package com.ftk.tpip.provider.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import org.junit.jupiter.api.Test;

class ProviderTest {

    @Test
    void createsActiveProviderAndNormalizesText() {
        Provider provider = Provider.create(
                AssetCode.of("payment.channel"),
                "  Payment Channel  ",
                ProviderType.CHANNEL,
                "  test provider  ",
                "integration-team");

        assertNull(provider.id());
        assertEquals("Payment Channel", provider.providerName());
        assertEquals("test provider", provider.description());
        assertEquals(ProviderStatus.ACTIVE, provider.status());
        assertEquals(0, provider.rowVersion());
    }

    @Test
    void rejectsBlankProviderName() {
        assertThrows(IllegalArgumentException.class, () -> Provider.create(
                AssetCode.of("payment.channel"),
                " ",
                ProviderType.CHANNEL,
                null,
                "integration-team"));
    }
}
