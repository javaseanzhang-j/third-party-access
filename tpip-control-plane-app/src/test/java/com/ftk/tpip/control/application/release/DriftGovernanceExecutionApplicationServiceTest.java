package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.release.domain.model.DriftGovernanceExecution;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.repository.DriftGovernanceExecutionRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DriftGovernanceExecutionApplicationServiceTest {
    private static final Instant CREATED = Instant.now().minus(Duration.ofDays(5));

    @Test
    void explicitlyMaterializesImmutablePolicyAndEvaluationSnapshotIdempotently() {
        var ledger = new Ledger();
        var service = service(ledger, VerificationDriftReviewStatus.OPEN);

        var first = service.materialize(23, 11, "governance-owner");
        var second = service.materialize(23, 11, "governance-owner");

        assertEquals(first.id(), second.id());
        assertEquals(0, first.reminderCount());
        assertEquals(2, first.maximumReminders());
        assertEquals("workspace-owner", first.ownerCode());
        assertEquals(64, first.aggregationKey().length());
        assertEquals(64, first.evaluationChecksum().length());
        assertTrue(first.policySnapshotDocument().contains("policyVersionId"));
        assertEquals(1, ledger.writes);
    }

    @Test
    void rejectsResolvedReportWithoutCreatingLedgerState() {
        var ledger = new Ledger();
        var service = service(ledger, VerificationDriftReviewStatus.ACCEPTED);

        assertThrows(IllegalArgumentException.class, () -> service.materialize(23, 11, "owner"));
        assertEquals(0, ledger.writes);
    }

    @Test
    void listsWorkspaceScopedDueCandidatesWithoutGuessingReservationState() {
        var ledger = new Ledger();
        var service = service(ledger, VerificationDriftReviewStatus.OPEN);
        service.materialize(23, 11, "owner");

        var result = service.dueCandidates(23, 25);

        assertEquals(1, result.size());
        assertEquals(23, ledger.candidateWorkspaceId);
        assertEquals(25, ledger.candidateLimit);
        assertThrows(IllegalArgumentException.class, () -> service.dueCandidates(0, 25));
        assertThrows(IllegalArgumentException.class, () -> service.dueCandidates(23, 101));
    }

    private static DriftGovernanceExecutionApplicationService service(Ledger ledger,
            VerificationDriftReviewStatus status) {
        VerificationDriftWorkbenchRepository workbench = new Workbench(status);
        DriftGovernancePolicyResolver resolver = workspaceId ->
                new DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy(23L,
                        DriftGovernancePolicyApplicationService.ResolutionSource.WORKSPACE_POLICY, 2L, 7L,
                        Duration.ofHours(24), Duration.ofHours(6), Duration.ofHours(6), 2,
                        "workspace-owner", List.of(VerificationDriftKind.EVIDENCE_CHANGED), List.of());
        var evaluator = new VerificationDriftGovernanceEvaluationService(workbench, resolver);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        return new DriftGovernanceExecutionApplicationService(ledger, evaluator, json,
                new CanonicalJsonService(json));
    }

    private static final class Workbench implements VerificationDriftWorkbenchRepository {
        private final VerificationDriftReviewStatus status;
        private Workbench(VerificationDriftReviewStatus status) { this.status = status; }
        @Override public Optional<ReportRow> findReport(long reportId) {
            return Optional.of(new ReportRow(11, 1, 23, 6, 25, 2, 1, status, 0, null, CREATED, CREATED));
        }
        @Override public List<DriftItem> findItems(List<Long> reportIds) {
            return List.of(new DriftItem(11, 1, "fixture.response", VerificationDriftKind.RESULT_CHANGED));
        }
        @Override public List<ReportRow> findReports(Query query) { throw new UnsupportedOperationException(); }
        @Override public long countReports(Query query) { throw new UnsupportedOperationException(); }
        @Override public Summary summarize(Query query) { throw new UnsupportedOperationException(); }
        @Override public List<GroupRow> group(Query query, int limit) { throw new UnsupportedOperationException(); }
    }

    private static final class Ledger implements DriftGovernanceExecutionRepository {
        private DriftGovernanceExecution stored;
        private int writes;
        private long candidateWorkspaceId;
        private int candidateLimit;
        @Override public DriftGovernanceExecution materialize(DriftGovernanceExecution value, String actor) {
            writes++;
            stored = new DriftGovernanceExecution(1L, value.driftReportId(), value.workspaceId(),
                    value.policySource(), value.policyId(), value.policyVersionId(), value.aggregationKey(),
                    value.ownerCode(), value.status(), value.maximumReminders(), value.reminderIntervalSeconds(),
                    value.reminderCount(),
                    value.nextReminderAt(), value.policySnapshotDocument(), value.evaluationDocument(),
                    value.evaluationChecksum(), 0, actor, Instant.now(), Instant.now());
            return stored;
        }
        @Override public Optional<DriftGovernanceExecution> findById(long id) { return Optional.ofNullable(stored); }
        @Override public Optional<DriftGovernanceExecution> findByReportId(long reportId) {
            return Optional.ofNullable(stored);
        }
        @Override public List<DriftGovernanceExecution> findByWorkspaceId(long workspaceId) {
            return stored == null ? List.of() : List.of(stored);
        }
        @Override public List<DriftGovernanceExecution> findDueWithoutActiveBatch(Instant now, int limit) {
            return stored == null ? List.of() : List.of(stored);
        }
        @Override public List<DriftGovernanceExecution> findDueWithoutActiveBatch(
                long workspaceId, Instant now, int limit) {
            candidateWorkspaceId = workspaceId;
            candidateLimit = limit;
            return stored == null ? List.of() : List.of(stored);
        }
    }
}
