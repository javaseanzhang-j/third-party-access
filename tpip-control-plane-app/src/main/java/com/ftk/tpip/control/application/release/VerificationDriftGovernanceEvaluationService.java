package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository.Scope;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationDriftGovernanceEvaluationService {
    private final VerificationDriftWorkbenchRepository workbench;
    private final DriftGovernancePolicyResolver policies;

    public VerificationDriftGovernanceEvaluationService(VerificationDriftWorkbenchRepository workbench,
            DriftGovernancePolicyResolver policies) {
        this.workbench = workbench;
        this.policies = policies;
    }

    @Transactional(readOnly = true)
    public EvaluationPage evaluate(long workspaceId, int page, int size) {
        return evaluate(workspaceId, page, size, Instant.now());
    }

    @Transactional(readOnly = true)
    public ReportEvaluationResult evaluateReport(long workspaceId, long reportId) {
        return evaluateReport(workspaceId, reportId, Instant.now());
    }

    ReportEvaluationResult evaluateReport(long workspaceId, long reportId, Instant now) {
        if (workspaceId <= 0 || reportId <= 0)
            throw new IllegalArgumentException("workspaceId and reportId must be positive");
        var policy = policies.resolve(workspaceId);
        var row = workbench.findReport(reportId).orElseThrow(() ->
                new IllegalArgumentException("DriftReport does not exist: " + reportId));
        if (row.workspaceId() != workspaceId)
            throw new IllegalArgumentException("DriftReport does not belong to Workspace: " + workspaceId);
        var items = workbench.findItems(List.of(reportId));
        return new ReportEvaluationResult(snapshot(policy), evaluate(row, items, policy, now));
    }

    EvaluationPage evaluate(long workspaceId, int page, int size, Instant now) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        int offset = offset(page, size);
        var policy = policies.resolve(workspaceId);
        var query = new VerificationDriftWorkbenchRepository.Query(workspaceId, Scope.ACTIONABLE, null, null,
                false, now.minus(policy.overdueAfter()), offset, size);
        var rows = workbench.findReports(query);
        Map<Long, List<VerificationDriftWorkbenchRepository.DriftItem>> items = workbench
                .findItems(rows.stream().map(VerificationDriftWorkbenchRepository.ReportRow::reportId).toList())
                .stream().collect(Collectors.groupingBy(VerificationDriftWorkbenchRepository.DriftItem::reportId));
        List<ReportEvaluation> evaluations = rows.stream().map(row -> evaluate(row,
                items.getOrDefault(row.reportId(), List.of()), policy, now)).toList();
        return new EvaluationPage(snapshot(policy), evaluations, page, size, workbench.countReports(query));
    }

    private static ReportEvaluation evaluate(VerificationDriftWorkbenchRepository.ReportRow row,
            List<VerificationDriftWorkbenchRepository.DriftItem> items,
            DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy policy, Instant now) {
        List<DriftEvaluation> drifts = items.stream().map(item -> {
            boolean kindSuppressed = policy.suppressedDriftKinds().contains(item.driftKind());
            boolean checkSuppressed = policy.suppressedCheckCodes().contains(item.checkCode());
            return new DriftEvaluation(item.itemNo(), item.checkCode(), item.driftKind(),
                    kindSuppressed || checkSuppressed, kindSuppressed, checkSuppressed);
        }).toList();
        boolean fullySuppressed = !drifts.isEmpty() && drifts.stream().allMatch(DriftEvaluation::suppressed);
        Instant dueAt = row.createdAt().plus(policy.overdueAfter());
        boolean actionable = row.reviewStatus() == VerificationDriftReviewStatus.OPEN
                || row.reviewStatus() == VerificationDriftReviewStatus.ACKNOWLEDGED;
        boolean overdue = actionable && !now.isBefore(dueAt);
        return new ReportEvaluation(row.reportId(), row.reviewStatus(), row.rowVersion(), row.createdAt(), dueAt,
                overdue, fullySuppressed, actionable && overdue && !fullySuppressed, drifts);
    }

    private static PolicySnapshot snapshot(
            DriftGovernancePolicyApplicationService.ResolvedGovernancePolicy value) {
        return new PolicySnapshot(value.source(), value.policyId(), value.policyVersionId(),
                value.overdueAfter().toSeconds(), value.aggregationWindow().toSeconds(),
                value.reminderInterval().toSeconds(), value.maximumReminders(), value.ownerCode(),
                value.suppressedDriftKinds(), value.suppressedCheckCodes());
    }

    private static int offset(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        long value = (long) page * size;
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("page offset is too large");
        return (int) value;
    }

    public record EvaluationPage(PolicySnapshot policy, List<ReportEvaluation> items, int page, int size,
            long totalElements) {}
    public record ReportEvaluationResult(PolicySnapshot policy, ReportEvaluation report) {}
    public record PolicySnapshot(DriftGovernancePolicyApplicationService.ResolutionSource source, Long policyId,
            Long policyVersionId, long overdueAfterSeconds, long aggregationWindowSeconds,
            long reminderIntervalSeconds, int maximumReminders, String ownerCode,
            List<VerificationDriftKind> suppressedDriftKinds, List<String> suppressedCheckCodes) {}
    public record ReportEvaluation(long reportId, VerificationDriftReviewStatus reviewStatus, long rowVersion,
            Instant createdAt, Instant dueAt, boolean overdue, boolean fullySuppressed, boolean reminderCandidate,
            List<DriftEvaluation> drifts) {}
    public record DriftEvaluation(int itemNo, String checkCode, VerificationDriftKind driftKind, boolean suppressed,
            boolean kindSuppressed, boolean checkSuppressed) {}
}
