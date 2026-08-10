package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GlobalImpactJobQueryServiceTest {
    @Test
    void exposesStableProgressPagingAndAllowedActions() {
        var ready = row(GlobalDriftPolicyImpactJobStatus.READY, Instant.now().plusSeconds(600), 10, 10, 0, 0, 0);
        var repository = proxy((name, args) -> switch (name) {
            case "findJobs" -> new GlobalImpactJobQueryRepository.JobPage(List.of(ready), 21);
            default -> null;
        });
        var page = service(repository).jobs(new GlobalImpactJobQueryService.JobFilter(
                Set.of(GlobalDriftPolicyImpactJobStatus.READY), Set.of(GlobalImpactJobPriority.HIGH),
                7L, " local-user ", " candidate ", null, null), 1, 10);
        assertEquals(21, page.totalElements());
        assertEquals(3, page.totalPages());
        assertTrue(page.hasNext());
        assertEquals(100, page.items().getFirst().progress().progressPercent());
        assertTrue(enabled(page.items().getFirst(), "SEAL"));
        assertTrue(enabled(page.items().getFirst(), "CANCEL"));
        assertFalse(enabled(page.items().getFirst(), "RETRY_FAILED"));
    }

    @Test
    void expiredJobDisablesMutationEvenBeforeMaintenanceChangesStatus() {
        var expired = row(GlobalDriftPolicyImpactJobStatus.PENDING, Instant.now().minusSeconds(1), 2, 0, 0, 2, 0);
        var repository = proxy((name, args) -> name.equals("findJob") ? Optional.of(expired) : null);
        var detail = service(repository).job(expired.jobId());
        assertTrue(detail.summary().expired());
        assertFalse(enabled(detail.summary(), "REPRIORITIZE"));
        assertEquals("JOB_EXPIRED", detail.summary().allowedActions().getFirst().disabledReasonCode());
    }

    @Test
    void structuresStoredImpactEvidenceAndTimelineForUi() {
        String evidence = """
                {"parameterChanges":{"overdueAfterSecondsDelta":60,"aggregationWindowSecondsDelta":0,
                "reminderIntervalSecondsDelta":-10,"maximumRemindersDelta":1,"ownerChanged":true,
                "suppressedDriftKindsChanged":false,"suppressedCheckCodesChanged":true},
                "summary":{"actionableReports":9,"currentOverdueReports":3,"candidateOverdueReports":5,
                "newlyOverdueReports":2,"noLongerOverdueReports":0,"currentSuppressedReports":1,
                "candidateSuppressedReports":2,"newlySuppressedReports":1,"noLongerSuppressedReports":0,
                "currentReminderCandidates":2,"candidateReminderCandidates":4,
                "addedReminderCandidates":2,"removedReminderCandidates":0},"totalChangedReports":4}
                """;
        var impact = new GlobalImpactJobQueryRepository.WorkspaceImpactRow("job-1", 3, "ws", "Workspace",
                "LOCAL", WorkspaceRiskLevel.HIGH, 0, null, null, null, null, null, null,
                GlobalDriftPolicyImpactJobItemStatus.SUCCEEDED, 1, null, null, "snapshot-1", "a".repeat(64),
                evidence, Instant.now().plusSeconds(60), null, null, Instant.now(), Instant.now());
        var timeline = new GlobalImpactJobQueryRepository.TimelineRow("event-1", "CREATED", "local-user",
                "created", null, Instant.now());
        var repository = proxy((name, args) -> switch (name) {
            case "findJob" -> Optional.of(row(GlobalDriftPolicyImpactJobStatus.READY,
                    Instant.now().plusSeconds(60), 1, 1, 0, 0, 0));
            case "findWorkspaceImpacts" -> new GlobalImpactJobQueryRepository.WorkspaceImpactPage(List.of(impact), 1);
            case "findTimeline" -> List.of(timeline, new GlobalImpactJobQueryRepository.TimelineRow(
                    "event-2", "UPDATED", "local-user", "updated", "reason", Instant.now()));
            default -> null;
        });
        var service = service(repository);
        var page = service.workspaceImpacts("job-1", Set.of(), Set.of(), null, 0, 20);
        assertEquals(2, page.items().getFirst().impactComparison().summary().newlyOverdueReports());
        assertEquals(-10, page.items().getFirst().impactComparison().parameterChanges().reminderIntervalSecondsDelta());
        var timelinePage = service.timeline("job-1", 1);
        assertEquals(1, timelinePage.items().getFirst().sequence());
        assertTrue(timelinePage.truncated());
    }

    @Test
    void exposesFullJobImpactAggregationInsteadOfPageLocalCounts() {
        var repository = proxy((name, args) -> switch (name) {
            case "findJob" -> Optional.of(row(GlobalDriftPolicyImpactJobStatus.READY,
                    Instant.now().plusSeconds(60), 12, 10, 1, 1, 0));
            case "summarizeWorkspaceImpacts" -> new GlobalImpactJobQueryRepository.WorkspaceImpactSummary(
                    12, 3, 4, 3, 2, 1, 0, 10, 1, 27, 6, 4);
            default -> null;
        });

        var summary = service(repository).workspaceImpactSummary("job-1");

        assertEquals(12, summary.workspaceCount());
        assertEquals(2, summary.risks().critical());
        assertEquals(10, summary.statuses().succeeded());
        assertEquals(27, summary.impactTotals().changedReports());
        assertEquals(6, summary.impactTotals().newlyOverdueReports());
    }

    private static boolean enabled(GlobalImpactJobQueryService.JobListItem item, String action) {
        return item.allowedActions().stream().filter(value -> value.action().equals(action)).findFirst().orElseThrow().enabled();
    }

    private static GlobalImpactJobQueryService service(GlobalImpactJobQueryRepository repository) {
        return new GlobalImpactJobQueryService(repository, new ObjectMapper());
    }

    private static GlobalImpactJobQueryRepository.JobRow row(GlobalDriftPolicyImpactJobStatus status,
            Instant expiresAt, int workspaceCount, int succeeded, int failed, int pending, int running) {
        Instant now = Instant.now();
        return new GlobalImpactJobQueryRepository.JobRow("job-1", 7, "candidate", "Candidate", 8, 2,
                "DRAFT", "a".repeat(64), "b".repeat(64), workspaceCount, pending, running, succeeded, failed,
                status, GlobalImpactJobPriority.HIGH, now.minusSeconds(120), expiresAt, null, 3, 2,
                now.minusSeconds(60), now.minusSeconds(30), null, null,
                "local-user", now.minusSeconds(120), "local-user", now.minusSeconds(30));
    }

    @SuppressWarnings("unchecked")
    private static GlobalImpactJobQueryRepository proxy(Handler handler) {
        return (GlobalImpactJobQueryRepository) Proxy.newProxyInstance(
                GlobalImpactJobQueryRepository.class.getClassLoader(),
                new Class<?>[] {GlobalImpactJobQueryRepository.class},
                (proxy, method, args) -> handler.call(method.getName(), args == null ? new Object[0] : args));
    }
    private interface Handler { Object call(String name, Object[] args); }
}
