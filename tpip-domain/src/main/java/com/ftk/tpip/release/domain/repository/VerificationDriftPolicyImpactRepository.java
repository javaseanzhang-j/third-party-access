package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.VerificationDriftKind;
import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import java.time.Instant;
import java.util.List;

public interface VerificationDriftPolicyImpactRepository {
    SummaryRow summarize(Query query);
    long countChanged(Query query);
    List<ReportRow> findChanged(Query query, int offset, int limit);

    record Query(long workspaceId, Instant currentOverdueBefore, Instant candidateOverdueBefore,
            List<VerificationDriftKind> currentSuppressedKinds, List<String> currentSuppressedChecks,
            List<VerificationDriftKind> candidateSuppressedKinds, List<String> candidateSuppressedChecks) {
        public Query {
            if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
            if (currentOverdueBefore == null || candidateOverdueBefore == null)
                throw new IllegalArgumentException("overdue boundaries must not be null");
            currentSuppressedKinds = List.copyOf(currentSuppressedKinds);
            currentSuppressedChecks = List.copyOf(currentSuppressedChecks);
            candidateSuppressedKinds = List.copyOf(candidateSuppressedKinds);
            candidateSuppressedChecks = List.copyOf(candidateSuppressedChecks);
        }
    }

    record SummaryRow(long actionableReports, long currentOverdueReports, long candidateOverdueReports,
            long newlyOverdueReports, long noLongerOverdueReports,
            long currentSuppressedReports, long candidateSuppressedReports,
            long newlySuppressedReports, long noLongerSuppressedReports,
            long currentReminderCandidates, long candidateReminderCandidates,
            long addedReminderCandidates, long removedReminderCandidates) {}

    record ReportRow(long reportId, VerificationDriftReviewStatus reviewStatus, long rowVersion,
            Instant createdAt, boolean currentOverdue, boolean candidateOverdue,
            boolean currentSuppressed, boolean candidateSuppressed,
            boolean currentReminderCandidate, boolean candidateReminderCandidate) {}
}
