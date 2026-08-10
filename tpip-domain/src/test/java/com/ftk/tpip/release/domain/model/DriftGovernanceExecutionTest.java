package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DriftGovernanceExecutionTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void requiresPolicyReferencesToMatchResolutionSource() {
        assertThrows(IllegalArgumentException.class, () -> execution("BUILT_IN_DEFAULT", 1L, 2L));
        assertThrows(IllegalArgumentException.class, () -> execution("WORKSPACE_POLICY", null, null));
    }

    @Test
    void rejectsConsumedReminderBudgetBeyondMaximum() {
        assertThrows(IllegalArgumentException.class, () -> new DriftGovernanceExecution(1L, 2, 3,
                "WORKSPACE_POLICY", 1L, 2L, HASH, "owner", DriftGovernanceExecutionStatus.READY,
                2, 3600, 3, Instant.EPOCH, "{}", "{}", HASH, 0, "tester", Instant.EPOCH, Instant.EPOCH));
    }

    private static DriftGovernanceExecution execution(String source, Long policyId, Long versionId) {
        return new DriftGovernanceExecution(1L, 2, 3, source, policyId, versionId, HASH, "owner",
                DriftGovernanceExecutionStatus.READY, 2, 3600, 0, Instant.EPOCH, "{}", "{}", HASH, 0,
                "tester", Instant.EPOCH, Instant.EPOCH);
    }
}
