package com.ftk.tpip.provider.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import org.junit.jupiter.api.Test;

class ProviderEndpointTest {

    @Test
    void createsNormalizedDraftEndpoint() {
        ProviderEndpoint endpoint = ProviderEndpoint.draft(
                1,
                AssetCode.of("payment.order.query"),
                "dev",
                EndpointScheme.HTTPS,
                "https://api.example.com/",
                "/v1/orders/{orderId}",
                EndpointHttpMethod.GET,
                "application/json",
                "UTF-8",
                1000,
                3000,
                5000,
                null,
                null,
                "{}",
                "0".repeat(64));

        assertEquals("https://api.example.com", endpoint.baseUrl());
        assertEquals(EndpointLifecycleStatus.DRAFT, endpoint.lifecycleStatus());
        assertEquals(0, endpoint.revisionNo());
    }

    @Test
    void rejectsSchemeMismatch() {
        assertThrows(IllegalArgumentException.class, () -> ProviderEndpoint.draft(
                1,
                AssetCode.of("payment.order.query"),
                "dev",
                EndpointScheme.HTTPS,
                "http://api.example.com",
                "/v1/orders",
                EndpointHttpMethod.GET,
                null,
                "UTF-8",
                1000,
                3000,
                5000,
                null,
                null,
                null,
                "0".repeat(64)));
    }

    @Test
    void rejectsTotalTimeoutShorterThanReadTimeout() {
        assertThrows(IllegalArgumentException.class, () -> ProviderEndpoint.draft(
                1,
                AssetCode.of("payment.order.query"),
                "dev",
                EndpointScheme.HTTPS,
                "https://api.example.com",
                "/v1/orders",
                EndpointHttpMethod.GET,
                null,
                "UTF-8",
                1000,
                5000,
                3000,
                null,
                null,
                null,
                "0".repeat(64)));
    }
}
