package com.ftk.tpip.provider.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import com.ftk.tpip.shared.SemanticVersion;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ProviderContractTest {

    @Test
    void createsActiveStableContract() {
        ProviderContract contract = ProviderContract.create(
                10,
                AssetCode.of("payment.order.query"),
                "Payment order query",
                ProtocolType.HTTP,
                "provider API");

        assertEquals(ContractStatus.ACTIVE, contract.status());
        assertEquals(0, contract.rowVersion());
    }

    @Test
    void requiresAtLeastOnePrimarySchema() {
        assertThrows(IllegalArgumentException.class, () -> ProviderContractVersion.draft(
                1,
                SemanticVersion.parse("1.0.0"),
                null,
                null,
                "{}",
                null,
                null,
                "0".repeat(64)));
    }

    @Test
    void publishedVersionRequiresTimestamp() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderContractVersion(
                1L,
                1,
                1,
                SemanticVersion.parse("1.0.0"),
                "{}",
                null,
                null,
                null,
                null,
                "0".repeat(64),
                ContractLifecycleStatus.PUBLISHED,
                null,
                Instant.now()));
    }
}
