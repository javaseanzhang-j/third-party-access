package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationDriftGovernanceEvaluationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void evaluatesPolicyDeadlineAndItemLevelSuppressionsWithoutMutatingReviews() {
        var repository = new Repository();
        DriftGovernancePolicyResolver policies = workspaceId ->
                new DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy(
                23L, DriftGovernancePolicyApplicationService.ResolutionSource.WORKSPACE_POLICY, 2L, 7L,
                Duration.ofHours(24), Duration.ofHours(6), Duration.ofHours(6), 2, "workspace-owner",
                List.of(VerificationDriftKind.EVIDENCE_CHANGED), List.of("BUNDLE_PREVIEW"));
        var service = new VerificationDriftGovernanceEvaluationService(repository, policies);

        var page = service.evaluate(23, 0, 20, NOW);

        assertEquals(2L, page.policy().policyId());
        assertEquals(7L, page.policy().policyVersionId());
        assertEquals("workspace-owner", page.policy().ownerCode());
        assertEquals(2, page.items().size());
        assertTrue(page.items().get(0).overdue());
        assertTrue(page.items().get(0).fullySuppressed());
        assertFalse(page.items().get(0).reminderCandidate());
        assertTrue(page.items().get(0).drifts().get(0).kindSuppressed());
        assertTrue(page.items().get(0).drifts().get(1).checkSuppressed());
        assertTrue(page.items().get(1).reminderCandidate());
    }

    private static final class Repository implements VerificationDriftWorkbenchRepository {
        @Override public java.util.Optional<ReportRow> findReport(long reportId) {
            return findReports(null).stream().filter(value -> value.reportId() == reportId).findFirst();
        }
        @Override public List<ReportRow> findReports(Query query) {
            if (query != null) {
                assertEquals(23L, query.workspaceId());
                assertEquals(NOW.minus(Duration.ofHours(24)), query.overdueBefore());
            }
            return List.of(
                    new ReportRow(1, 1, 23, 6, 25, 7, 2, VerificationDriftReviewStatus.OPEN,
                            0, null, NOW.minus(Duration.ofHours(48)), NOW.minus(Duration.ofHours(48))),
                    new ReportRow(2, 1, 23, 6, 26, 7, 1, VerificationDriftReviewStatus.ACKNOWLEDGED,
                            3, null, NOW.minus(Duration.ofHours(48)), NOW.minus(Duration.ofHours(1))));
        }
        @Override public long countReports(Query query) { return 2; }
        @Override public List<DriftItem> findItems(List<Long> reportIds) {
            return List.of(
                    new DriftItem(1, 1, "fixture.response", VerificationDriftKind.EVIDENCE_CHANGED),
                    new DriftItem(1, 2, "BUNDLE_PREVIEW", VerificationDriftKind.RESULT_CHANGED),
                    new DriftItem(2, 1, "fixture.request", VerificationDriftKind.RESULT_CHANGED));
        }
        @Override public Summary summarize(Query query) { throw new UnsupportedOperationException(); }
        @Override public List<GroupRow> group(Query query, int limit) { throw new UnsupportedOperationException(); }
    }
}
