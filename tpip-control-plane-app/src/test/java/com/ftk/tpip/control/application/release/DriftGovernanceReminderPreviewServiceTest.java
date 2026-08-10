package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.*;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernanceExecutionRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

class DriftGovernanceReminderPreviewServiceTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void groupsEligibleExecutionsAndCreatesAutomationDraftOnly() {
        var executions = new Executions(List.of(execution(1, HASH), execution(2, HASH)));
        var created = new ArrayList<List<Long>>();
        var batches = new DriftGovernanceReminderBatchApplicationService(null, null, null, null, null) {
            @Override public BatchDetail createAutomated(long workspace, String environment, List<Long> ids,
                    String actor) {
                created.add(List.copyOf(ids)); return null;
            }
        };
        var service = new DriftGovernanceReminderPreviewService(executions, batches);

        var result = service.createDueDrafts(Instant.now(), 100, 10, "local", "preview-system");

        assertEquals(2, result.eligibleExecutions());
        assertEquals(1, result.createdBatches());
        assertEquals(List.of(1L, 2L), created.getFirst());
    }

    private static DriftGovernanceExecution execution(long id, String aggregation) {
        Instant now = Instant.now();
        return new DriftGovernanceExecution(id, id + 10, 23, "BUILT_IN_DEFAULT", null, null,
                aggregation, "owner", DriftGovernanceExecutionStatus.READY, 3, 3600, 0,
                now.minusSeconds(1), "{}", "{}", HASH, 0, "operator", now, now);
    }

    private record Executions(List<DriftGovernanceExecution> values) implements DriftGovernanceExecutionRepository {
        @Override public List<DriftGovernanceExecution> findDueWithoutActiveBatch(Instant now, int limit) {
            return values;
        }
        @Override public List<DriftGovernanceExecution> findDueWithoutActiveBatch(
                long workspaceId, Instant now, int limit) { return findDueWithoutActiveBatch(now, limit); }
        @Override public Optional<DriftGovernanceExecution> findById(long id) { return Optional.empty(); }
        @Override public Optional<DriftGovernanceExecution> findByReportId(long id) { return Optional.empty(); }
        @Override public List<DriftGovernanceExecution> findByWorkspaceId(long id) { return values; }
        @Override public DriftGovernanceExecution materialize(DriftGovernanceExecution value, String actor) {
            throw new UnsupportedOperationException();
        }
    }
}
