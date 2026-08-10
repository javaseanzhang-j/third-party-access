package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.DriftGovernancePolicyScope;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyStatus;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersionStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyAssetQueryRepository;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyAssetQueryRepository.ImpactJobRow;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyAssetQueryRepository.PolicyRow;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyAssetQueryRepository.VersionRow;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriftGovernancePolicyAssetQueryService {
    private final DriftGovernancePolicyAssetQueryRepository queries;

    public DriftGovernancePolicyAssetQueryService(DriftGovernancePolicyAssetQueryRepository queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public PolicyPage policies(PolicyFilter filter, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("invalid page request");
        PolicyFilter safe = filter == null ? new PolicyFilter(null, null, null, null) : filter;
        String keyword = normalize(safe.keyword());
        var result = queries.findPolicies(new DriftGovernancePolicyAssetQueryRepository.PolicyQuery(
                safe.scopes(), safe.statuses(), safe.workspaceId(), keyword), Math.multiplyExact(page, size), size);
        long totalPages = result.totalElements() == 0 ? 0 : (result.totalElements() + size - 1) / size;
        return new PolicyPage(result.items().stream().map(this::policy).toList(), page, size,
                result.totalElements(), totalPages);
    }

    @Transactional(readOnly = true)
    public PolicyDetail policy(long policyId, int recentJobLimit) {
        requireLimit(recentJobLimit);
        PolicyRow policy = requirePolicy(policyId);
        return new PolicyDetail(policy(policy), queries.findVersions(policyId).stream().map(this::version).toList(),
                queries.findRecentImpactJobs(policyId, null, recentJobLimit).stream().map(this::job).toList());
    }

    @Transactional(readOnly = true)
    public VersionDetail version(long policyId, long versionId, int recentJobLimit) {
        requireLimit(recentJobLimit);
        PolicyRow policy = requirePolicy(policyId);
        VersionRow version = queries.findVersion(policyId, versionId)
                .orElseThrow(() -> new IllegalArgumentException("Governance policy version does not exist: " + versionId));
        return new VersionDetail(policy(policy), version(version),
                queries.findRecentImpactJobs(policyId, versionId, recentJobLimit).stream().map(this::job).toList());
    }

    private PolicyRow requirePolicy(long policyId) {
        return queries.findPolicy(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Governance policy does not exist: " + policyId));
    }

    private PolicyAsset policy(PolicyRow row) {
        WorkspaceAssignment workspace = row.workspaceId() == null ? null : new WorkspaceAssignment(
                row.workspaceId(), row.workspaceCode(), row.workspaceName(), row.environmentCode());
        CurrentVersion current = row.currentVersionId() == null ? null : new CurrentVersion(
                row.currentVersionId(), row.currentVersionNo(), row.currentVersionStatus());
        return new PolicyAsset(row.policyId(), row.policyCode(), row.policyName(), row.scope(), workspace,
                row.status(), current, row.versionCount(), row.impactJobCount(), row.rowVersion(),
                row.createdAt(), row.updatedAt());
    }

    private VersionAsset version(VersionRow row) {
        return new VersionAsset(row.versionId(), row.policyId(), row.versionNo(), row.overdueAfterSeconds(),
                row.aggregationWindowSeconds(), row.reminderIntervalSeconds(), row.maximumReminders(),
                row.ownerCode(), row.suppressedDriftKinds(), row.suppressedCheckCodes(), row.contentChecksum(),
                row.lifecycleStatus(), row.currentlySelected(), row.impactJobCount(), row.publishedAt(), row.createdAt());
    }

    private ImpactJob job(ImpactJobRow row) {
        int completed = row.succeededCount() + row.failedCount();
        int percent = row.workspaceCount() == 0 ? 0 : Math.min(100, completed * 100 / row.workspaceCount());
        return new ImpactJob(row.jobId(), row.policyId(), row.versionId(), row.versionNo(), row.status(),
                row.priority(), row.workspaceCount(), row.succeededCount(), row.failedCount(), percent,
                row.sealedSnapshotId(), row.expiresAt(), row.createdAt(), row.updatedAt());
    }

    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim(); return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireLimit(int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("recentJobLimit must be between 1 and 100");
    }

    public record PolicyFilter(Set<DriftGovernancePolicyScope> scopes,
            Set<DriftGovernancePolicyStatus> statuses, Long workspaceId, String keyword) {}
    public record PolicyPage(List<PolicyAsset> items, int page, int size, long totalElements, long totalPages) {}
    public record PolicyDetail(PolicyAsset policy, List<VersionAsset> versions, List<ImpactJob> recentImpactJobs) {}
    public record VersionDetail(PolicyAsset policy, VersionAsset version, List<ImpactJob> recentImpactJobs) {}
    public record WorkspaceAssignment(long workspaceId, String workspaceCode, String workspaceName,
            String environmentCode) {}
    public record CurrentVersion(long versionId, Integer versionNo,
            DriftGovernancePolicyVersionStatus lifecycleStatus) {}
    public record PolicyAsset(long policyId, String policyCode, String policyName,
            DriftGovernancePolicyScope scope, WorkspaceAssignment workspace,
            DriftGovernancePolicyStatus status, CurrentVersion currentVersion,
            long versionCount, long impactJobCount, long rowVersion, Instant createdAt, Instant updatedAt) {}
    public record VersionAsset(long versionId, long policyId, int versionNo, long overdueAfterSeconds,
            long aggregationWindowSeconds, long reminderIntervalSeconds, int maximumReminders,
            String ownerCode, List<VerificationDriftKind> suppressedDriftKinds,
            List<String> suppressedCheckCodes, String contentChecksum,
            DriftGovernancePolicyVersionStatus lifecycleStatus, boolean currentlySelected,
            long impactJobCount, Instant publishedAt, Instant createdAt) {}
    public record ImpactJob(String jobId, long policyId, long versionId, int versionNo,
            GlobalDriftPolicyImpactJobStatus status, GlobalImpactJobPriority priority,
            int workspaceCount, int succeededCount, int failedCount, int progressPercent,
            String sealedSnapshotId, Instant expiresAt, Instant createdAt, Instant updatedAt) {}
}
