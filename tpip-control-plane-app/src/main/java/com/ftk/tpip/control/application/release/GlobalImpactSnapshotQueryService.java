package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicy;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersion;
import com.ftk.tpip.release.domain.model.DriftPolicyImpactSnapshot;
import com.ftk.tpip.release.domain.model.WorkspaceRiskLevel;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stable UI-facing projection for immutable global impact snapshot assets. */
@Service
public class GlobalImpactSnapshotQueryService {
    private final GlobalDriftPolicyImpactSnapshotRepository snapshots;
    private final DriftPolicyImpactSnapshotRepository workspaceSnapshots;
    private final DriftGovernancePolicyRepository policies;
    private final ReleaseRepository releases;
    private final ObjectMapper json;

    public GlobalImpactSnapshotQueryService(GlobalDriftPolicyImpactSnapshotRepository snapshots,
            DriftPolicyImpactSnapshotRepository workspaceSnapshots,
            DriftGovernancePolicyRepository policies, ReleaseRepository releases, ObjectMapper json) {
        this.snapshots = snapshots;
        this.workspaceSnapshots = workspaceSnapshots;
        this.policies = policies;
        this.releases = releases;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public SnapshotDetail snapshot(String snapshotId) {
        var value = find(snapshotId);
        var policy = policy(value.candidatePolicyId());
        var version = version(value.candidatePolicyId(), value.candidateVersionId());
        Instant now = Instant.now();
        return new SnapshotDetail(value.snapshotId(), candidate(policy, version), value.candidateChecksum(),
                value.coverageChecksum(), value.impactChecksum(), value.workspaceCount(), value.createdBy(),
                value.createdAt(), value.expiresAt(), value.expired(now),
                new Consumption(value.publishUsedBy(), value.publishUsedAt(),
                        value.activationUsedBy(), value.activationUsedAt()));
    }

    @Transactional(readOnly = true)
    public WorkspaceSnapshotPage workspaceSnapshots(String snapshotId, int page, int size) {
        var header = find(snapshotId);
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        long offset = (long) page * size;
        if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("page offset is too large");
        var items = snapshots.findItems(snapshotId, (int) offset, size).stream().map(item -> workspaceSnapshots
                .findById(item.workspaceSnapshotId()).orElseThrow(() -> new IllegalStateException(
                        "Global impact snapshot child is missing: " + item.workspaceSnapshotId())))
                .map(this::workspaceSnapshot).toList();
        int totalPages = (int) Math.min(Integer.MAX_VALUE, (header.workspaceCount() + size - 1L) / size);
        return new WorkspaceSnapshotPage(snapshotId, items, page, size, header.workspaceCount(), totalPages,
                (long) (page + 1) * size < header.workspaceCount());
    }

    private WorkspaceSnapshot workspaceSnapshot(DriftPolicyImpactSnapshot value) {
        var workspace = releases.findWorkspace(value.workspaceId()).orElseThrow(() ->
                new IllegalStateException("Snapshot Workspace is missing: " + value.workspaceId()));
        CurrentPolicy current = null;
        if (value.currentPolicyId() != null) {
            var policy = policy(value.currentPolicyId());
            var version = version(value.currentPolicyId(), value.currentVersionId());
            current = new CurrentPolicy(policy.id(), policy.policyCode().value(), policy.policyName(),
                    version.id(), version.versionNo(), value.currentChecksum());
        }
        return new WorkspaceSnapshot(value.snapshotId(), value.workspaceId(), workspace.workspaceCode().value(),
                workspace.workspaceName(), workspace.environmentCode(), workspace.riskLevel(), current,
                value.impactChecksum(), value.expiresAt(), parse(value.impactDocument()));
    }

    private ImpactComparison parse(String document) {
        try {
            JsonNode root = json.readTree(document);
            JsonNode changes = root.path("parameterChanges");
            JsonNode summary = root.path("summary");
            return new ImpactComparison(new ParameterChanges(changes.path("overdueAfterSecondsDelta").asLong(),
                    changes.path("aggregationWindowSecondsDelta").asLong(),
                    changes.path("reminderIntervalSecondsDelta").asLong(),
                    changes.path("maximumRemindersDelta").asInt(), changes.path("ownerChanged").asBoolean(),
                    changes.path("suppressedDriftKindsChanged").asBoolean(),
                    changes.path("suppressedCheckCodesChanged").asBoolean()),
                    new ImpactSummary(summary.path("actionableReports").asLong(),
                            summary.path("currentOverdueReports").asLong(),
                            summary.path("candidateOverdueReports").asLong(),
                            summary.path("newlyOverdueReports").asLong(),
                            summary.path("noLongerOverdueReports").asLong(),
                            summary.path("currentReminderCandidates").asLong(),
                            summary.path("candidateReminderCandidates").asLong(),
                            summary.path("addedReminderCandidates").asLong(),
                            summary.path("removedReminderCandidates").asLong()),
                    root.path("totalChangedReports").asLong());
        } catch (Exception exception) {
            throw new IllegalStateException("Stored Workspace impact evidence is invalid", exception);
        }
    }

    private DriftGovernancePolicy policy(long policyId) {
        return policies.findById(policyId).orElseThrow(() ->
                new IllegalStateException("Snapshot candidate policy is missing: " + policyId));
    }

    private com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactSnapshot find(String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) throw new IllegalArgumentException("snapshotId is invalid");
        return snapshots.findById(snapshotId).orElseThrow(() ->
                new IllegalArgumentException("GlobalDriftPolicyImpactSnapshot does not exist: " + snapshotId));
    }

    private DriftGovernancePolicyVersion version(long policyId, long versionId) {
        return policies.findVersion(policyId, versionId).orElseThrow(() ->
                new IllegalStateException("Snapshot policy version is missing: " + versionId));
    }

    private static CandidatePolicy candidate(DriftGovernancePolicy policy, DriftGovernancePolicyVersion version) {
        return new CandidatePolicy(policy.id(), policy.policyCode().value(), policy.policyName(), version.id(),
                version.versionNo(), version.lifecycleStatus().name());
    }

    public record SnapshotDetail(String snapshotId, CandidatePolicy candidatePolicy, String candidateChecksum,
            String coverageChecksum, String impactChecksum, int workspaceCount, String createdBy,
            Instant createdAt, Instant expiresAt, boolean expired, Consumption consumption) {}
    public record CandidatePolicy(long policyId, String policyCode, String policyName, long versionId,
            int versionNo, String versionStatus) {}
    public record Consumption(String publishUsedBy, Instant publishUsedAt, String activationUsedBy,
            Instant activationUsedAt) {}
    public record WorkspaceSnapshotPage(String snapshotId, List<WorkspaceSnapshot> items, int page, int size,
            long totalElements, int totalPages, boolean hasNext) {}
    public record WorkspaceSnapshot(String snapshotId, long workspaceId, String workspaceCode,
            String workspaceName, String environmentCode, WorkspaceRiskLevel riskLevel,
            CurrentPolicy currentPolicy, String impactChecksum, Instant expiresAt, ImpactComparison impact) {}
    public record CurrentPolicy(long policyId, String policyCode, String policyName, long versionId,
            int versionNo, String checksum) {}
    public record ImpactComparison(ParameterChanges parameterChanges, ImpactSummary summary,
            long totalChangedReports) {}
    public record ParameterChanges(long overdueAfterSecondsDelta, long aggregationWindowSecondsDelta,
            long reminderIntervalSecondsDelta, int maximumRemindersDelta, boolean ownerChanged,
            boolean suppressedDriftKindsChanged, boolean suppressedCheckCodesChanged) {}
    public record ImpactSummary(long actionableReports, long currentOverdueReports,
            long candidateOverdueReports, long newlyOverdueReports, long noLongerOverdueReports,
            long currentReminderCandidates, long candidateReminderCandidates,
            long addedReminderCandidates, long removedReminderCandidates) {}
}
