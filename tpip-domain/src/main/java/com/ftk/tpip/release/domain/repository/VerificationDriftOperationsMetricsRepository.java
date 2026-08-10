package com.ftk.tpip.release.domain.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface VerificationDriftOperationsMetricsRepository {
    SnapshotRow snapshot(Query query);
    ResolutionRow resolutions(Query query);
    OperationRow operations(Query query);
    List<AssigneeRow> assignees(Query query);
    List<DailyRow> daily(Query query);

    record Query(long workspaceId, Instant windowStart, Instant windowEnd, Instant overdueBefore) {
        public Query {
            if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
            if (windowStart == null || windowEnd == null || overdueBefore == null)
                throw new IllegalArgumentException("metric time boundaries must not be null");
            if (!windowStart.isBefore(windowEnd))
                throw new IllegalArgumentException("windowStart must be before windowEnd");
            if (overdueBefore.isAfter(windowEnd))
                throw new IllegalArgumentException("overdueBefore must not be after windowEnd");
        }
    }

    record SnapshotRow(long actionableReports, long assignedActionableReports, long overdueReports,
            long unassignedOverdueReports, Instant oldestActionableAt, double averageAgeHours) {}

    record ResolutionRow(long resolvedReports, long resolvedWithinSla, long acceptedReports, long dismissedReports,
            double averageResolutionHours, double maximumResolutionHours) {}

    record OperationRow(long commands, long previewedCommands, long appliedCommands, long rejectedCommands,
            long requestedItems, long eligibleItems, long appliedItems, long rejectedItems) {}

    record AssigneeRow(String assigneeCode, long actionableReports, long overdueReports,
            Instant oldestActionableAt) {}

    record DailyRow(LocalDate date, long createdReports, long resolvedReports,
            long previewedCommands, long appliedCommands, long rejectedCommands) {}
}
