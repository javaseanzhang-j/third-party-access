package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Stable read-only projection port for Workspace asset exploration. */
public interface WorkspaceAssetQueryRepository {
    WorkspacePage findWorkspaces(WorkspaceQuery query, int offset, int limit);
    Optional<WorkspaceRow> findWorkspace(long workspaceId);
    List<BaselineRow> findBaselines(long workspaceId, int limit);
    List<DriftReportRow> findDriftReports(long workspaceId, int limit);
    List<ImpactEvidenceRow> findImpactEvidence(long workspaceId, int limit);

    record WorkspaceQuery(Set<WorkspaceLifecycleStatus> lifecycleStatuses, Set<WorkspaceRiskLevel> riskLevels,
            String environmentCode, String keyword) {}
    record WorkspacePage(List<WorkspaceRow> items, long totalElements) {}
    record EffectivePolicy(Long policyId, String policyCode, String policyName, Long versionId,
            Integer versionNo, String resolutionSource) {}
    record LatestBaseline(Long baselineId, long fixtureSuiteVersionId, long sourceVerificationRunId,
            String baselineChecksum, Instant createdAt) {}
    record WorkspaceRow(long workspaceId, String workspaceCode, String workspaceName, Long baseBundleId,
            String environmentCode, WorkspaceLifecycleStatus lifecycleStatus, WorkspaceRiskLevel riskLevel,
            String ownerCode, EffectivePolicy effectivePolicy, LatestBaseline latestBaseline, long baselineCount,
            long driftReportCount, long actionableDriftCount, long changedItemCount, long rowVersion,
            Instant createdAt, Instant updatedAt) {}
    record BaselineRow(long baselineId, long fixtureSuiteVersionId, long sourceVerificationRunId,
            String baselineChecksum, Long predecessorBaselineId, Long acceptedDriftReportId, Instant createdAt) {}
    record DriftReportRow(long reportId, long baselineId, long verificationRunId,
            VerificationDriftStatus driftStatus, int comparedCheckCount, int driftCount,
            VerificationDriftReviewStatus reviewStatus, String assigneeCode, Long successorBaselineId,
            Instant createdAt, Instant updatedAt) {}
    record ImpactEvidenceRow(String jobId, long candidatePolicyId, String candidatePolicyCode,
            String candidatePolicyName, long candidateVersionId, int candidateVersionNo,
            GlobalDriftPolicyImpactJobStatus jobStatus, GlobalImpactJobPriority priority,
            GlobalDriftPolicyImpactJobItemStatus itemStatus, int attemptCount, String workspaceSnapshotId,
            String impactChecksum, String sealedSnapshotId, Instant jobCreatedAt, Instant finishedAt) {}
}
