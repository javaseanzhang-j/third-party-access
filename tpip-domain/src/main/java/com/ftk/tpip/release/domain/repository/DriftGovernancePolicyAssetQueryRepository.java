package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.DriftGovernancePolicyScope;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyStatus;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersionStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Stable read-only projection port for governance policy asset exploration. */
public interface DriftGovernancePolicyAssetQueryRepository {
    PolicyPage findPolicies(PolicyQuery query, int offset, int limit);
    Optional<PolicyRow> findPolicy(long policyId);
    List<VersionRow> findVersions(long policyId);
    Optional<VersionRow> findVersion(long policyId, long versionId);
    List<ImpactJobRow> findRecentImpactJobs(long policyId, Long versionId, int limit);

    record PolicyQuery(Set<DriftGovernancePolicyScope> scopes, Set<DriftGovernancePolicyStatus> statuses,
            Long workspaceId, String keyword) {}

    record PolicyPage(List<PolicyRow> items, long totalElements) {}

    record PolicyRow(long policyId, String policyCode, String policyName, DriftGovernancePolicyScope scope,
            Long workspaceId, String workspaceCode, String workspaceName, String environmentCode,
            DriftGovernancePolicyStatus status, Long currentVersionId, Integer currentVersionNo,
            DriftGovernancePolicyVersionStatus currentVersionStatus, long versionCount, long impactJobCount,
            long rowVersion, Instant createdAt, Instant updatedAt) {}

    record VersionRow(long versionId, long policyId, int versionNo, long overdueAfterSeconds,
            long aggregationWindowSeconds, long reminderIntervalSeconds, int maximumReminders,
            String ownerCode, List<VerificationDriftKind> suppressedDriftKinds,
            List<String> suppressedCheckCodes, String contentChecksum,
            DriftGovernancePolicyVersionStatus lifecycleStatus, boolean currentlySelected,
            long impactJobCount, Instant publishedAt, Instant createdAt) {}

    record ImpactJobRow(String jobId, long policyId, long versionId, int versionNo,
            GlobalDriftPolicyImpactJobStatus status, GlobalImpactJobPriority priority,
            int workspaceCount, int succeededCount, int failedCount, String sealedSnapshotId,
            Instant expiresAt, Instant createdAt, Instant updatedAt) {}
}
