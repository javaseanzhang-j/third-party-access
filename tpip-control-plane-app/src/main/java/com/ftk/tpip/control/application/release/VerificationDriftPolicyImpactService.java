package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.DriftGovernancePolicy;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyScope;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersion;
import com.ftk.tpip.release.domain.model.DriftGovernancePolicyVersionStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftPolicyImpactRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationDriftPolicyImpactService {
    private final DriftGovernancePolicyResolver resolver;
    private final DriftGovernancePolicyRepository policies;
    private final VerificationDriftPolicyImpactRepository impacts;

    public VerificationDriftPolicyImpactService(DriftGovernancePolicyResolver resolver,
            DriftGovernancePolicyRepository policies, VerificationDriftPolicyImpactRepository impacts) {
        this.resolver = resolver;
        this.policies = policies;
        this.impacts = impacts;
    }

    @Transactional(readOnly = true)
    public Impact compare(long workspaceId, long candidatePolicyId, long candidateVersionId, int page, int size) {
        return compare(workspaceId, candidatePolicyId, candidateVersionId, page, size, Instant.now());
    }

    Impact compare(long workspaceId, long candidatePolicyId, long candidateVersionId,
            int page, int size, Instant now) {
        if (workspaceId <= 0 || candidatePolicyId <= 0 || candidateVersionId <= 0)
            throw new IllegalArgumentException("workspaceId, candidatePolicyId and candidateVersionId must be positive");
        int offset = offset(page, size);
        var current = resolver.resolve(workspaceId);
        DriftGovernancePolicy candidatePolicy = policies.findById(candidatePolicyId).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernancePolicy does not exist: " + candidatePolicyId));
        DriftGovernancePolicyVersion candidate = policies.findVersion(candidatePolicyId, candidateVersionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "DriftGovernancePolicyVersion does not exist: " + candidateVersionId));
        validateScope(workspaceId, candidatePolicy);
        String currentChecksum = current.policyId() == null ? null : policies
                .findVersion(current.policyId(), current.policyVersionId()).orElseThrow(() ->
                        new IllegalStateException("Resolved governance policy version no longer exists"))
                .contentChecksum();
        var query = new VerificationDriftPolicyImpactRepository.Query(workspaceId,
                now.minus(current.overdueAfter()), now.minus(candidate.overdueAfter()),
                current.suppressedDriftKinds(), current.suppressedCheckCodes(),
                candidate.suppressedDriftKinds(), candidate.suppressedCheckCodes());
        var summary = impacts.summarize(query);
        long changed = impacts.countChanged(query);
        List<ChangedReport> reports = impacts.findChanged(query, offset, size).stream().map(value ->
                new ChangedReport(value.reportId(), value.reviewStatus(), value.rowVersion(), value.createdAt(),
                        value.createdAt().plus(current.overdueAfter()),
                        value.createdAt().plus(candidate.overdueAfter()), value.currentOverdue(),
                        value.candidateOverdue(), value.currentSuppressed(), value.candidateSuppressed(),
                        value.currentReminderCandidate(), value.candidateReminderCandidate())).toList();
        return new Impact(workspaceId, now,
                new CurrentPolicy(current.source(), current.policyId(), current.policyVersionId(), currentChecksum,
                        current.overdueAfter().toSeconds(), current.aggregationWindow().toSeconds(),
                        current.reminderInterval().toSeconds(), current.maximumReminders(), current.ownerCode(),
                        current.suppressedDriftKinds().size(), current.suppressedCheckCodes().size()),
                new CandidatePolicy(candidatePolicy.scope(), candidatePolicy.id(), candidate.id(), candidate.versionNo(),
                        candidate.lifecycleStatus(), candidate.contentChecksum(), candidate.overdueAfter().toSeconds(),
                        candidate.aggregationWindow().toSeconds(), candidate.reminderInterval().toSeconds(),
                        candidate.maximumReminders(), candidate.ownerCode(), candidate.suppressedDriftKinds().size(),
                        candidate.suppressedCheckCodes().size()),
                changes(current, candidate), summary(summary), reports, page, size, changed);
    }

    private static ParameterChanges changes(
            DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy current,
            DriftGovernancePolicyVersion candidate) {
        return new ParameterChanges(
                candidate.overdueAfter().toSeconds() - current.overdueAfter().toSeconds(),
                candidate.aggregationWindow().toSeconds() - current.aggregationWindow().toSeconds(),
                candidate.reminderInterval().toSeconds() - current.reminderInterval().toSeconds(),
                candidate.maximumReminders() - current.maximumReminders(),
                !candidate.ownerCode().equals(current.ownerCode()),
                !candidate.suppressedDriftKinds().equals(current.suppressedDriftKinds()),
                !candidate.suppressedCheckCodes().equals(current.suppressedCheckCodes()));
    }

    private static ImpactSummary summary(VerificationDriftPolicyImpactRepository.SummaryRow value) {
        return new ImpactSummary(value.actionableReports(), value.currentOverdueReports(),
                value.candidateOverdueReports(), value.newlyOverdueReports(), value.noLongerOverdueReports(),
                value.currentSuppressedReports(), value.candidateSuppressedReports(), value.newlySuppressedReports(),
                value.noLongerSuppressedReports(), value.currentReminderCandidates(),
                value.candidateReminderCandidates(), value.addedReminderCandidates(),
                value.removedReminderCandidates());
    }

    private static void validateScope(long workspaceId, DriftGovernancePolicy policy) {
        if (policy.scope() == DriftGovernancePolicyScope.WORKSPACE
                && !Long.valueOf(workspaceId).equals(policy.workspaceId()))
            throw new IllegalArgumentException("Candidate policy does not belong to Workspace: " + workspaceId);
    }

    private static int offset(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        long value = (long) page * size;
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("page offset is too large");
        return (int) value;
    }

    public record Impact(long workspaceId, Instant snapshotAt, CurrentPolicy currentPolicy,
            CandidatePolicy candidatePolicy, ParameterChanges parameterChanges, ImpactSummary summary,
            List<ChangedReport> items, int page, int size, long totalChangedReports) {}
    public record CurrentPolicy(DriftGovernancePolicyApplicationService.ResolutionSource source,
            Long policyId, Long policyVersionId, String contentChecksum, long overdueAfterSeconds,
            long aggregationWindowSeconds, long reminderIntervalSeconds, int maximumReminders,
            String ownerCode, int suppressedDriftKindCount, int suppressedCheckCodeCount) {}
    public record CandidatePolicy(DriftGovernancePolicyScope scope, long policyId, long policyVersionId,
            int versionNo, DriftGovernancePolicyVersionStatus lifecycleStatus, String contentChecksum,
            long overdueAfterSeconds, long aggregationWindowSeconds, long reminderIntervalSeconds,
            int maximumReminders, String ownerCode, int suppressedDriftKindCount,
            int suppressedCheckCodeCount) {}
    public record ParameterChanges(long overdueAfterSecondsDelta, long aggregationWindowSecondsDelta,
            long reminderIntervalSecondsDelta, int maximumRemindersDelta, boolean ownerChanged,
            boolean suppressedDriftKindsChanged, boolean suppressedCheckCodesChanged) {}
    public record ImpactSummary(long actionableReports, long currentOverdueReports,
            long candidateOverdueReports, long newlyOverdueReports, long noLongerOverdueReports,
            long currentSuppressedReports, long candidateSuppressedReports, long newlySuppressedReports,
            long noLongerSuppressedReports, long currentReminderCandidates,
            long candidateReminderCandidates, long addedReminderCandidates,
            long removedReminderCandidates) {}
    public record ChangedReport(long reportId, VerificationDriftReviewStatus reviewStatus, long rowVersion,
            Instant createdAt, Instant currentDueAt, Instant candidateDueAt,
            boolean currentOverdue, boolean candidateOverdue, boolean currentSuppressed,
            boolean candidateSuppressed, boolean currentReminderCandidate,
            boolean candidateReminderCandidate) {}
}
