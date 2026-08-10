package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import java.time.Instant;
import java.util.List;

public interface VerificationDriftWorkbenchRepository {
    List<ReportRow> findReports(Query query);
    java.util.Optional<ReportRow> findReport(long reportId);
    long countReports(Query query);
    List<DriftItem> findItems(List<Long> reportIds);
    Summary summarize(Query query);
    List<GroupRow> group(Query query, int limit);

    enum Scope { ACTIONABLE, RESOLVED, ALL }
    record Query(Long workspaceId, Scope scope, VerificationDriftKind driftKind, String checkCode,
            String assigneeCode, boolean overdueOnly, Instant overdueBefore, int offset, int limit) {
        public Query {
            if (workspaceId != null && workspaceId <= 0)
                throw new IllegalArgumentException("workspaceId must be positive");
            if (scope == null) throw new IllegalArgumentException("scope must not be null");
            if (checkCode != null) {
                checkCode = checkCode.trim();
                if (checkCode.isEmpty() || checkCode.length() > 180)
                    throw new IllegalArgumentException("checkCode is invalid");
            }
            if (assigneeCode != null) {
                assigneeCode = assigneeCode.trim();
                if (assigneeCode.isEmpty() || assigneeCode.length() > 100)
                    throw new IllegalArgumentException("assigneeCode is invalid");
            }
            if (overdueBefore == null) throw new IllegalArgumentException("overdueBefore must not be null");
            if (offset < 0 || limit < 1 || limit > 100)
                throw new IllegalArgumentException("invalid workbench pagination");
        }
        public Query(Long workspaceId, Scope scope, VerificationDriftKind driftKind, String checkCode,
                boolean overdueOnly, Instant overdueBefore, int offset, int limit) {
            this(workspaceId, scope, driftKind, checkCode, null, overdueOnly, overdueBefore, offset, limit);
        }
    }

    record ReportRow(long reportId, long baselineId, long workspaceId, long fixtureSuiteVersionId,
            long verificationRunId, int comparedCheckCount, int driftCount,
            VerificationDriftReviewStatus reviewStatus, long rowVersion, Long successorBaselineId,
            String assigneeCode, String assignedBy, Instant assignedAt, String assignmentNote,
            Instant createdAt, Instant updatedAt) {
        public ReportRow(long reportId, long baselineId, long workspaceId, long fixtureSuiteVersionId,
                long verificationRunId, int comparedCheckCount, int driftCount,
                VerificationDriftReviewStatus reviewStatus, long rowVersion, Long successorBaselineId,
                Instant createdAt, Instant updatedAt) {
            this(reportId, baselineId, workspaceId, fixtureSuiteVersionId, verificationRunId, comparedCheckCount,
                    driftCount, reviewStatus, rowVersion, successorBaselineId, null, null, null, null,
                    createdAt, updatedAt);
        }
    }
    record DriftItem(long reportId, int itemNo, String checkCode, VerificationDriftKind driftKind) {}
    record Summary(long totalReports, long actionableReports, long assignedActionableReports,
            long unassignedActionableReports, long overdueReports, long acceptedReports,
            long dismissedReports, long affectedWorkspaces, long changedItems) {
        public Summary(long totalReports, long actionableReports, long overdueReports, long acceptedReports,
                long dismissedReports, long affectedWorkspaces, long changedItems) {
            this(totalReports, actionableReports, 0, actionableReports, overdueReports, acceptedReports,
                    dismissedReports, affectedWorkspaces, changedItems);
        }
    }
    record GroupRow(String checkCode, VerificationDriftKind driftKind, long reportCount, long actionableReportCount,
            long overdueReportCount, long affectedWorkspaceCount, Instant latestReportAt) {}
}
