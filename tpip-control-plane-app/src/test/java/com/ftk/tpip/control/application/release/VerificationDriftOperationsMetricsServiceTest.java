package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ftk.tpip.release.domain.repository.VerificationDriftOperationsMetricsRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationDriftOperationsMetricsServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");

    @Test
    void calculatesSlaAssignmentResolutionAndCommandRatesAndFillsMissingDays() {
        var service = service(new Repository());

        var metrics = service.metrics(23, 3, Duration.ofHours(72), NOW);

        assertEquals(Instant.parse("2026-08-07T00:00:00Z"), metrics.windowStart());
        assertEquals(VerificationDriftOperationsMetricsService.SlaMode.EXPLICIT_ANALYSIS_OVERRIDE,
                metrics.slaPolicy().mode());
        assertEquals(Duration.ofHours(48).toSeconds(), metrics.slaPolicy().configuredSlaSeconds());
        assertEquals(Duration.ofHours(72).toSeconds(), metrics.slaPolicy().effectiveSlaSeconds());
        assertEquals(6, metrics.backlog().withinSlaReports());
        assertEquals(4, metrics.backlog().unassignedReports());
        assertEquals(new BigDecimal("40.00"), metrics.backlog().breachRatePercent());
        assertEquals(new BigDecimal("60.00"), metrics.backlog().assignmentCoveragePercent());
        assertEquals(100, metrics.backlog().oldestAgeHours());
        assertEquals(new BigDecimal("80.00"), metrics.resolution().slaCompliancePercent());
        assertEquals(new BigDecimal("90.13"), metrics.resolution().maximumResolutionHours());
        assertEquals(new BigDecimal("83.33"), metrics.commands().executionSuccessPercent());
        assertEquals(2, metrics.assignees().size());
        assertEquals(3, metrics.daily().size());
        assertEquals(new VerificationDriftOperationsMetricsService.DailyMetric(
                LocalDate.parse("2026-08-07"), 0, 0, 0, 0, 0), metrics.daily().getFirst());
        assertEquals(3, metrics.daily().get(1).createdReports());
        assertEquals(0, metrics.daily().getLast().createdReports());
    }

    @Test
    void usesResolvedImmutablePolicyVersionWhenNoAnalysisOverrideIsProvided() {
        var repository = new Repository();

        var metrics = service(repository).metrics(23, 3, null, NOW);

        assertEquals(48, metrics.slaHours());
        assertEquals(Duration.ofHours(48).toSeconds(), metrics.slaSeconds());
        assertEquals(VerificationDriftOperationsMetricsService.SlaMode.POLICY, metrics.slaPolicy().mode());
        assertEquals(DriftGovernancePolicyApplicationService.ResolutionSource.WORKSPACE_POLICY,
                metrics.slaPolicy().source());
        assertEquals(2L, metrics.slaPolicy().policyId());
        assertEquals(7L, metrics.slaPolicy().policyVersionId());
        assertEquals(NOW.minus(Duration.ofHours(48)), repository.snapshotQuery.overdueBefore());
    }

    @Test
    void rejectsUnboundedMetricWindowsAndInvalidSla() {
        var service = service(new Repository());

        assertThrows(IllegalArgumentException.class,
                () -> service.metrics(23, 91, Duration.ofHours(72), NOW));
        assertThrows(IllegalArgumentException.class,
                () -> service.metrics(23, 30, Duration.ZERO, NOW));
    }

    private static VerificationDriftOperationsMetricsService service(Repository repository) {
        DriftGovernancePolicyResolver policies = workspaceId ->
                new DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy(
                        workspaceId, DriftGovernancePolicyApplicationService.ResolutionSource.WORKSPACE_POLICY,
                        2L, 7L, Duration.ofHours(48), Duration.ofHours(6), Duration.ofHours(12), 3,
                        "workspace-owner", List.of(), List.of());
        return new VerificationDriftOperationsMetricsService(repository, policies);
    }

    private static final class Repository implements VerificationDriftOperationsMetricsRepository {
        private Query snapshotQuery;
        @Override public SnapshotRow snapshot(Query query) {
            snapshotQuery = query;
            return new SnapshotRow(10, 6, 4, 2, NOW.minus(Duration.ofHours(100)), 30.123);
        }
        @Override public ResolutionRow resolutions(Query query) {
            return new ResolutionRow(5, 4, 3, 2, 50.555, 90.125);
        }
        @Override public OperationRow operations(Query query) {
            return new OperationRow(8, 2, 5, 1, 20, 17, 15, 3);
        }
        @Override public List<AssigneeRow> assignees(Query query) {
            return List.of(
                    new AssigneeRow(null, 4, 2, NOW.minus(Duration.ofHours(100))),
                    new AssigneeRow("owner-a", 6, 2, NOW.minus(Duration.ofHours(80))));
        }
        @Override public List<DailyRow> daily(Query query) {
            return List.of(new DailyRow(LocalDate.parse("2026-08-08"), 3, 2, 2, 1, 1));
        }
    }
}
