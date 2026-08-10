package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobItemStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.WorkspaceRiskLevel;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Read-only projection port kept separate from the command aggregate repository. */
public interface GlobalImpactJobQueryRepository {
    JobPage findJobs(JobQuery query, int offset, int limit);
    Optional<JobRow> findJob(String jobId);
    List<JobRow> findStalledJobs(Instant now, Instant stalledBefore, int limit);
    WorkspaceImpactPage findWorkspaceImpacts(WorkspaceImpactQuery query, int offset, int limit);
    WorkspaceImpactSummary summarizeWorkspaceImpacts(String jobId);
    List<TimelineRow> findTimeline(String jobId, int limit);

    record JobQuery(Set<GlobalDriftPolicyImpactJobStatus> statuses, Set<GlobalImpactJobPriority> priorities,
            Long candidatePolicyId, String createdBy, String keyword, Instant createdFrom, Instant createdTo) {}

    record WorkspaceImpactQuery(String jobId, Set<GlobalDriftPolicyImpactJobItemStatus> statuses,
            Set<WorkspaceRiskLevel> riskLevels, String keyword) {}

    record JobPage(List<JobRow> items, long totalElements) {}

    record WorkspaceImpactPage(List<WorkspaceImpactRow> items, long totalElements) {}

    record WorkspaceImpactSummary(long workspaceCount,
            long lowRiskCount, long mediumRiskCount, long highRiskCount, long criticalRiskCount,
            long pendingCount, long runningCount, long succeededCount, long failedCount,
            long totalChangedReports, long newlyOverdueReports, long addedReminderCandidates) {}

    record JobRow(String jobId, long candidatePolicyId, String candidatePolicyCode,
            String candidatePolicyName, long candidateVersionId, int candidateVersionNo,
            String candidateVersionStatus, String candidateChecksum, String coverageChecksum,
            int workspaceCount, int pendingCount, int runningCount, int succeededCount, int failedCount,
            GlobalDriftPolicyImpactJobStatus status, GlobalImpactJobPriority priority,
            Instant snapshotAt, Instant expiresAt, String sealedSnapshotId, long rowVersion,
            long dispatchCount, Instant lastDispatchedAt, Instant lastProgressAt,
            String dispatchLeaseOwner, Instant dispatchLeaseUntil,
            String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {}

    record WorkspaceImpactRow(String jobId, long workspaceId, String workspaceCode, String workspaceName,
            String environmentCode, WorkspaceRiskLevel riskLevel, int itemOrder,
            Long currentPolicyId, String currentPolicyCode, String currentPolicyName,
            Long currentVersionId, Integer currentVersionNo, String currentChecksum,
            GlobalDriftPolicyImpactJobItemStatus status, int attemptCount,
            String leaseOwner, Instant leaseUntil, String workspaceSnapshotId,
            String impactChecksum, String impactDocument, Instant snapshotExpiresAt,
            String failureCode, String failureMessage, Instant startedAt, Instant finishedAt) {}

    record TimelineRow(String eventId, String eventType, String actorCode, String summary,
            String detail, Instant occurredAt) {}
}
