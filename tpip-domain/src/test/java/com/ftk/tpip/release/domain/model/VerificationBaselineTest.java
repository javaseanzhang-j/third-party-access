package com.ftk.tpip.release.domain.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class VerificationBaselineTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void acceptsImmutableBaselineAndConsistentReports() {
        assertDoesNotThrow(() -> new VerificationBaseline(1L, 2, 3, 4, "a".repeat(64), "{}", NOW));
        assertDoesNotThrow(() -> new VerificationDriftReport(1L, 2, 3,
                VerificationDriftStatus.NO_DRIFT, 7, 0, "{}", NOW));
        assertDoesNotThrow(() -> new VerificationDriftReport(2L, 2, 4,
                VerificationDriftStatus.DRIFTED, 7, 1, "{}", NOW));
        assertDoesNotThrow(() -> new VerificationDriftReview(2, VerificationDriftReviewStatus.ACKNOWLEDGED,
                1, "owner", NOW, "investigated", null, null, null, null, NOW, NOW));
        assertDoesNotThrow(() -> new VerificationBaseline(2L, 2, 3, 5, "b".repeat(64), "{}",
                1L, 2L, NOW));
        assertDoesNotThrow(() -> new VerificationDriftReview(3, VerificationDriftReviewStatus.OPEN, 1,
                "owner-b", "operator", NOW, "assigned for review", null, null, null,
                null, null, null, null, NOW, NOW));
    }

    @Test
    void rejectsInvalidChecksumAndInconsistentDriftCounts() {
        assertThrows(IllegalArgumentException.class,
                () -> new VerificationBaseline(null, 2, 3, 4, "invalid", "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationDriftReport(null, 2, 3,
                VerificationDriftStatus.NO_DRIFT, 7, 1, "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationDriftReport(null, 2, 3,
                VerificationDriftStatus.DRIFTED, 7, 0, "{}", null));
        assertThrows(IllegalArgumentException.class, () -> new VerificationDriftReview(2,
                VerificationDriftReviewStatus.ACCEPTED, 2, "owner", NOW, "investigated", "owner", NOW,
                "expected change", null, NOW, NOW));
        assertThrows(IllegalArgumentException.class, () -> new VerificationDriftReview(3,
                VerificationDriftReviewStatus.OPEN, 1, "owner-b", null, null, null,
                null, null, null, null, null, null, null, NOW, NOW));
    }
}
