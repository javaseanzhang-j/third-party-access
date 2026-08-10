package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationDriftWorkbenchServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void enrichesPagedReportsWithAgeDeadlineAndNormalizedDriftItems() {
        var service = new VerificationDriftWorkbenchService(new Repository());
        var filter = new VerificationDriftWorkbenchService.Filter(null,
                VerificationDriftWorkbenchRepository.Scope.ACTIONABLE, null, null, false, Duration.ofHours(72));

        var page = service.reports(filter, 0, 20, NOW);

        assertEquals(2, page.totalElements());
        assertEquals(2, page.items().size());
        assertEquals(96, page.items().get(0).ageHours());
        assertTrue(page.items().get(0).overdue());
        assertFalse(page.items().get(1).overdue());
        assertEquals("fixture.remote.response", page.items().get(0).drifts().getFirst().checkCode());
        assertEquals(VerificationDriftKind.RESULT_CHANGED,
                page.items().get(0).drifts().getFirst().driftKind());
    }

    @Test
    void exposesGovernanceSummaryAndDeterministicGroups() {
        var service = new VerificationDriftWorkbenchService(new Repository());
        var filter = new VerificationDriftWorkbenchService.Filter(null,
                VerificationDriftWorkbenchRepository.Scope.ALL, null, null, false, Duration.ofHours(72));

        var summary = service.summary(filter);
        var groups = service.groups(filter, 10);

        assertEquals(2, summary.totalReports());
        assertEquals(1, summary.overdueReports());
        assertEquals(2, summary.changedItems());
        assertEquals(1, groups.size());
        assertEquals(2, groups.getFirst().reportCount());
    }

    private static final class Repository implements VerificationDriftWorkbenchRepository {
        @Override public java.util.Optional<ReportRow> findReport(long reportId) {
            return findReports(null).stream().filter(value -> value.reportId() == reportId).findFirst();
        }
        @Override public List<ReportRow> findReports(Query query) {
            return List.of(
                    new ReportRow(1, 1, 23, 6, 25, 7, 1, VerificationDriftReviewStatus.OPEN,
                            0, null, NOW.minus(Duration.ofHours(96)), NOW.minus(Duration.ofHours(96))),
                    new ReportRow(2, 4, 23, 6, 26, 7, 1, VerificationDriftReviewStatus.ACKNOWLEDGED,
                            1, null, NOW.minus(Duration.ofHours(24)), NOW.minus(Duration.ofHours(1))));
        }
        @Override public long countReports(Query query) { return 2; }
        @Override public List<DriftItem> findItems(List<Long> reportIds) {
            return List.of(
                    new DriftItem(1, 1, "fixture.remote.response", VerificationDriftKind.RESULT_CHANGED),
                    new DriftItem(2, 1, "fixture.remote.response", VerificationDriftKind.RESULT_CHANGED));
        }
        @Override public Summary summarize(Query query) { return new Summary(2, 2, 1, 0, 0, 1, 2); }
        @Override public List<GroupRow> group(Query query, int limit) {
            return List.of(new GroupRow("fixture.remote.response", VerificationDriftKind.RESULT_CHANGED,
                    2, 2, 1, 1, NOW.minus(Duration.ofHours(24))));
        }
    }
}
