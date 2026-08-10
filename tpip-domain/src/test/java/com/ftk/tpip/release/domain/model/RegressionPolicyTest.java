package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.shared.AssetCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RegressionPolicyTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void acceptsVersionedActivePolicyAndScheduleCandidate() {
        RegressionPolicy policy = new RegressionPolicy(1L, AssetCode.of("customer.regression"),
                "Customer regression", 2, RegressionPolicyStatus.ACTIVE, 3L, 1, NOW, NOW);
        RegressionPolicyVersion version = new RegressionPolicyVersion(3L, 1, 2, 1, Duration.ofHours(1),
                Duration.ofMinutes(5), 3, RegressionPolicyVersionStatus.PUBLISHED, NOW, NOW);
        assertDoesNotThrow(() -> new RegressionScheduleCandidate(policy, version, "test", NOW, 0));
    }

    @Test
    void rejectsActivePolicyWithoutVersionAndUnsafeIntervals() {
        assertThrows(IllegalArgumentException.class, () -> new RegressionPolicy(1L,
                AssetCode.of("customer.regression"), "Customer regression", 2,
                RegressionPolicyStatus.ACTIVE, null, 0, NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new RegressionPolicyVersion(null, 1, 2, 0,
                Duration.ofSeconds(30), Duration.ofMinutes(5), 3,
                RegressionPolicyVersionStatus.DRAFT, null, null));
    }
}
