package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.VerificationDriftReviewStatus;
import com.ftk.tpip.release.domain.model.VerificationDriftKind;
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
public class VerificationDriftWorkbenchService {
    private final VerificationDriftWorkbenchRepository repository;

    public VerificationDriftWorkbenchService(VerificationDriftWorkbenchRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public WorkbenchPage reports(Filter filter, int page, int size) {
        return reports(filter, page, size, Instant.now());
    }

    WorkbenchPage reports(Filter filter, int page, int size, Instant now) {
        int offset = offset(page, size);
        var query = query(filter, offset, size, now);
        var rows = repository.findReports(query);
        Map<Long, List<VerificationDriftWorkbenchRepository.DriftItem>> items = repository
                .findItems(rows.stream().map(VerificationDriftWorkbenchRepository.ReportRow::reportId).toList())
                .stream().collect(Collectors.groupingBy(VerificationDriftWorkbenchRepository.DriftItem::reportId));
        List<WorkItem> workItems = rows.stream().map(row -> item(row,
                items.getOrDefault(row.reportId(), List.of()), filter.overdueAfter(), now)).toList();
        return new WorkbenchPage(workItems, page, size, repository.countReports(query));
    }

    @Transactional(readOnly = true)
    public WorkbenchSummary summary(Filter filter) {
        var value = repository.summarize(query(filter, 0, 1, Instant.now()));
        return new WorkbenchSummary(value.totalReports(), value.actionableReports(), value.assignedActionableReports(),
                value.unassignedActionableReports(), value.overdueReports(),
                value.acceptedReports(), value.dismissedReports(), value.affectedWorkspaces(), value.changedItems());
    }

    @Transactional(readOnly = true)
    public List<DriftGroup> groups(Filter filter, int limit) {
        return repository.group(query(filter, 0, 1, Instant.now()), limit).stream()
                .map(value -> new DriftGroup(value.checkCode(), value.driftKind(), value.reportCount(),
                        value.actionableReportCount(), value.overdueReportCount(), value.affectedWorkspaceCount(),
                        value.latestReportAt())).toList();
    }

    private static WorkItem item(VerificationDriftWorkbenchRepository.ReportRow row,
            List<VerificationDriftWorkbenchRepository.DriftItem> items, Duration overdueAfter, Instant now) {
        Instant dueAt = row.createdAt().plus(overdueAfter);
        boolean actionable = row.reviewStatus() == VerificationDriftReviewStatus.OPEN
                || row.reviewStatus() == VerificationDriftReviewStatus.ACKNOWLEDGED;
        boolean overdue = actionable && !now.isBefore(dueAt);
        long ageHours = Math.max(0, Duration.between(row.createdAt(), now).toHours());
        return new WorkItem(row.reportId(), row.baselineId(), row.workspaceId(), row.fixtureSuiteVersionId(),
                row.verificationRunId(), row.comparedCheckCount(), row.driftCount(), row.reviewStatus(),
                row.rowVersion(), row.successorBaselineId(), row.assigneeCode(), row.assignedBy(), row.assignedAt(),
                row.assignmentNote(), row.createdAt(), row.updatedAt(), ageHours, dueAt,
                overdue, items.stream().map(value -> new DriftSignature(value.itemNo(), value.checkCode(),
                        value.driftKind())).toList());
    }

    private static VerificationDriftWorkbenchRepository.Query query(Filter filter, int offset, int limit,
            Instant now) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        return new VerificationDriftWorkbenchRepository.Query(filter.workspaceId(), filter.scope(),
                filter.driftKind(), filter.checkCode(), filter.assigneeCode(), filter.overdueOnly(),
                now.minus(filter.overdueAfter()),
                offset, limit);
    }

    private static int offset(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        long value = (long) page * size;
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("page offset is too large");
        return (int) value;
    }

    public record Filter(Long workspaceId, Scope scope, VerificationDriftKind driftKind, String checkCode,
            String assigneeCode,
            boolean overdueOnly, Duration overdueAfter) {
        public Filter {
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
            if (overdueAfter == null || overdueAfter.compareTo(Duration.ofHours(1)) < 0
                    || overdueAfter.compareTo(Duration.ofDays(365)) > 0)
                throw new IllegalArgumentException("overdueAfter must be between 1 hour and 365 days");
        }
        public Filter(Long workspaceId, Scope scope, VerificationDriftKind driftKind, String checkCode,
                boolean overdueOnly, Duration overdueAfter) {
            this(workspaceId, scope, driftKind, checkCode, null, overdueOnly, overdueAfter);
        }
    }
    public record WorkbenchPage(List<WorkItem> items, int page, int size, long totalElements) {}
    public record WorkItem(long reportId, long baselineId, long workspaceId, long fixtureSuiteVersionId,
            long verificationRunId, int comparedCheckCount, int driftCount,
            VerificationDriftReviewStatus reviewStatus, long rowVersion, Long successorBaselineId,
            String assigneeCode, String assignedBy, Instant assignedAt, String assignmentNote,
            Instant createdAt, Instant updatedAt, long ageHours, Instant dueAt, boolean overdue,
            List<DriftSignature> drifts) {}
    public record DriftSignature(int itemNo, String checkCode, VerificationDriftKind driftKind) {}
    public record WorkbenchSummary(long totalReports, long actionableReports, long assignedActionableReports,
            long unassignedActionableReports, long overdueReports,
            long acceptedReports, long dismissedReports, long affectedWorkspaces, long changedItems) {}
    public record DriftGroup(String checkCode, VerificationDriftKind driftKind, long reportCount, long actionableReportCount,
            long overdueReportCount, long affectedWorkspaceCount, Instant latestReportAt) {}
}
