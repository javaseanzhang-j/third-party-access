package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.integration.domain.model.MappingAssetDirection;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixtureSuiteVersionTest {
    @Test
    void createsImmutableDraftSnapshot() {
        FixtureCase fixture = successfulFixture();
        FixtureSuiteVersion version = FixtureSuiteVersion.draft(7, "a".repeat(64), List.of(fixture));

        assertEquals(FixtureSuiteVersionStatus.DRAFT, version.lifecycleStatus());
        assertThrows(UnsupportedOperationException.class, () -> version.cases().add(fixture));
    }

    @Test
    void rejectsVersionWithoutCases() {
        assertThrows(IllegalArgumentException.class,
                () -> FixtureSuiteVersion.draft(7, "a".repeat(64), List.of()));
    }

    @Test
    void successfulFixtureRequiresExpectedDocument() {
        assertThrows(IllegalArgumentException.class,
                () -> new FixtureCase(null, 0, "customer.response", "customer response", 0,
                        FixtureExecutionMode.MAPPING, MappingAssetDirection.INBOUND_RESPONSE, "{}", null, true, null, null, null));
    }

    @Test
    void typedAssertionsReplaceLegacyExpectedDocument() {
        FixtureCase fixture = new FixtureCase(null, 0, "customer.assertions", "customer assertions", 0,
                FixtureExecutionMode.MAPPING, MappingAssetDirection.INBOUND_RESPONSE, "{}", null, true, null,
                "[{\"code\":\"success\",\"type\":\"SUCCESS\",\"expected\":true}]", null);

        assertEquals("customer.assertions", fixture.caseCode());
    }

    @Test
    void remoteCallRequiresOutboundDirectionAndTypedAssertions() {
        assertThrows(IllegalArgumentException.class, () -> new FixtureCase(null, 0, "remote", "remote", 0,
                FixtureExecutionMode.REMOTE_CALL, MappingAssetDirection.INBOUND_RESPONSE, "{}", null,
                true, null, "[{\"code\":\"status\",\"type\":\"HTTP_STATUS\",\"expected\":200}]", null));
        assertThrows(IllegalArgumentException.class, () -> new FixtureCase(null, 0, "remote", "remote", 0,
                FixtureExecutionMode.REMOTE_CALL, MappingAssetDirection.OUTBOUND_REQUEST, "{}", null,
                true, null, null, null));
    }

    private static FixtureCase successfulFixture() {
        return new FixtureCase(null, 0, "customer.response", "customer response", 0,
                FixtureExecutionMode.MAPPING, MappingAssetDirection.INBOUND_RESPONSE, "{\"customer_id\":\"1\"}",
                "{\"customerId\":\"1\"}", true, null, null, null);
    }
}
