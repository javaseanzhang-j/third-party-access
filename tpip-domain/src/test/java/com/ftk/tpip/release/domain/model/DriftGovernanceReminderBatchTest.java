package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class DriftGovernanceReminderBatchTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void dispatchedBatchRequiresOutboxAndDispatchEvidence() {
        assertThrows(IllegalArgumentException.class, () -> batch(DriftGovernanceReminderBatchStatus.DISPATCHED));
    }

    @Test
    void rejectsInvalidEnvironmentCode() {
        assertThrows(IllegalArgumentException.class, () -> new DriftGovernanceReminderBatch(null, "batch", 1,
                HASH, "Prod!", "owner", DriftGovernanceReminderBatchSource.MANUAL,
                DriftGovernanceReminderBatchStatus.DRAFT, 1, "{}", HASH, 0,
                null, null, null, "creator", null, null, null, null, null, null, null, null));
    }

    @Test
    void cancelledBatchRequiresReasonAndOperatorEvidence() {
        assertThrows(IllegalArgumentException.class, () -> batch(DriftGovernanceReminderBatchStatus.CANCELLED));
    }

    private static DriftGovernanceReminderBatch batch(DriftGovernanceReminderBatchStatus status) {
        return new DriftGovernanceReminderBatch(null, "batch", 1, HASH, "local", "owner",
                DriftGovernanceReminderBatchSource.MANUAL, status, 1, "{}", HASH, 0,
                null, null, null, "creator", null, null, null, null, null, null, null, null);
    }
}
