package com.ftk.tpip.access.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import org.junit.jupiter.api.Test;

class ProviderProductTest {
    @Test
    void createsAnActiveProductInsideOneProvider() {
        ProviderProduct product = ProviderProduct.create(7, AssetCode.of("sms"), "短信服务", "短信发送产品");

        assertEquals(7, product.providerId());
        assertEquals("sms", product.productCode().value());
        assertEquals("短信服务", product.productName());
        assertEquals(AccessChannelStatus.ACTIVE, product.status());
    }

    @Test
    void rejectsAProductWithoutAProviderOrReadableName() {
        assertThrows(IllegalArgumentException.class,
                () -> ProviderProduct.create(0, AssetCode.of("sms"), "短信服务", null));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderProduct.create(7, AssetCode.of("sms"), "  ", null));
    }
}
