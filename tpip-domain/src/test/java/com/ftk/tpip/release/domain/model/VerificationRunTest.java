package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VerificationRunTest {
    @Test
    void acceptsConsistentPassedEvidence() {
        var run = new VerificationRun(null, 1, 0, VerificationRunType.FULL, VerificationStatus.PASSED,
                3, 3, 0, null, "{}", Instant.EPOCH, Instant.EPOCH);
        assertEquals(3, run.passedCount());
    }

    @Test
    void acceptsRunningJobWithoutFinalCounts() {
        var run = new VerificationRun(1L, 1, 1, VerificationRunType.FULL, VerificationStatus.RUNNING,
                0, 0, 0, null, "{}", Instant.EPOCH, null);
        assertEquals(VerificationStatus.RUNNING, run.status());
    }

    @Test
    void rejectsPassedEvidenceWithFailure() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerificationRun(null, 1, 0, VerificationRunType.FULL, VerificationStatus.PASSED,
                        3, 2, 1, null, "{}", Instant.EPOCH, Instant.EPOCH));
    }

    @Test
    void rejectsCompletedJobWithoutFinishedAt() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerificationRun(null, 1, 0, VerificationRunType.FULL, VerificationStatus.FAILED,
                        1, 0, 1, null, "{}", Instant.EPOCH, null));
    }
}
