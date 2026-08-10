package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobItemStatus;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.model.WorkspaceRiskLevel;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository.JobRow;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalImpactJobQueryService {
    private final GlobalImpactJobQueryRepository queries;
    private final ObjectMapper json;

    public GlobalImpactJobQueryService(GlobalImpactJobQueryRepository queries, ObjectMapper json) {
        this.queries = queries; this.json = json;
    }

    @Transactional(readOnly = true)
    public JobPage jobs(JobFilter filter, int page, int size) {
        int offset = offset(page, size); JobFilter normalized = normalize(filter);
        var result = queries.findJobs(new GlobalImpactJobQueryRepository.JobQuery(normalized.statuses(),
                normalized.priorities(), normalized.candidatePolicyId(), normalized.createdBy(),
                normalized.keyword(), normalized.createdFrom(), normalized.createdTo()), offset, size);
        Instant now = Instant.now();
        List<JobListItem> items = result.items().stream().map(row -> listItem(row, now)).toList();
        return new JobPage(items, page, size, result.totalElements(), pages(result.totalElements(), size),
                (long) (page + 1) * size < result.totalElements());
    }

    @Transactional(readOnly = true)
    public JobDetail job(String jobId) {
        JobRow row = find(jobId); Instant now = Instant.now();
        return new JobDetail(listItem(row, now), row.candidateChecksum(), row.coverageChecksum(),
                row.snapshotAt(), row.sealedSnapshotId(), row.dispatchCount(), row.lastDispatchedAt(),
                row.lastProgressAt(), row.dispatchLeaseOwner(), row.dispatchLeaseUntil(),
                stalledSeconds(row, now), recovery(row, now), row.createdBy(), row.createdAt(),
                row.updatedBy(), row.updatedAt());
    }

    @Transactional(readOnly = true)
    public WorkspaceImpactPage workspaceImpacts(String jobId,
            Set<GlobalDriftPolicyImpactJobItemStatus> statuses, Set<WorkspaceRiskLevel> riskLevels,
            String keyword, int page, int size) {
        find(jobId); int offset = offset(page, size);
        var result = queries.findWorkspaceImpacts(new GlobalImpactJobQueryRepository.WorkspaceImpactQuery(jobId,
                copy(statuses), copy(riskLevels), optional(keyword, 180, "keyword")), offset, size);
        var items = result.items().stream().map(row -> new WorkspaceImpactItem(row.workspaceId(),
                row.workspaceCode(), row.workspaceName(), row.environmentCode(), row.riskLevel(), row.itemOrder(),
                new CurrentPolicy(row.currentPolicyId(), row.currentPolicyCode(), row.currentPolicyName(),
                        row.currentVersionId(), row.currentVersionNo(), row.currentChecksum()),
                row.status(), row.attemptCount(), row.leaseOwner(), row.leaseUntil(),
                row.failureCode(), row.failureMessage(), row.startedAt(), row.finishedAt(),
                comparison(row.workspaceSnapshotId(), row.impactChecksum(), row.impactDocument(),
                        row.snapshotExpiresAt()))).toList();
        return new WorkspaceImpactPage(jobId, items, page, size, result.totalElements(),
                pages(result.totalElements(), size), (long) (page + 1) * size < result.totalElements());
    }

    @Transactional(readOnly = true)
    public WorkspaceImpactSummary workspaceImpactSummary(String jobId) {
        String id = find(jobId).jobId();
        var value = queries.summarizeWorkspaceImpacts(id);
        return new WorkspaceImpactSummary(id, value.workspaceCount(),
                new RiskDistribution(value.lowRiskCount(), value.mediumRiskCount(),
                        value.highRiskCount(), value.criticalRiskCount()),
                new StatusDistribution(value.pendingCount(), value.runningCount(),
                        value.succeededCount(), value.failedCount()),
                new ImpactTotals(value.totalChangedReports(), value.newlyOverdueReports(),
                        value.addedReminderCandidates()));
    }

    @Transactional(readOnly = true)
    public Timeline timeline(String jobId, int limit) {
        find(jobId);
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        var found = queries.findTimeline(jobId, limit + 1);
        boolean truncated = found.size() > limit;
        var rows = truncated ? found.subList(0, limit) : found;
        var items = java.util.stream.IntStream.range(0, rows.size()).mapToObj(index -> {
            var row = rows.get(index);
            return new TimelineEntry(index + 1, row.eventId(), row.eventType(), row.actorCode(),
                    row.summary(), row.detail(), row.occurredAt());
        }).toList();
        return new Timeline(jobId, items, truncated);
    }

    private JobRow find(String jobId) {
        String id = required(jobId, 36, "jobId");
        return queries.findJob(id).orElseThrow(() ->
                new IllegalArgumentException("Global impact job does not exist: " + id));
    }

    private JobListItem listItem(JobRow row, Instant now) {
        int processed = row.succeededCount() + row.failedCount();
        int percent = row.workspaceCount() == 0 ? 100
                : (int) Math.min(100, Math.round(processed * 100.0 / row.workspaceCount()));
        var progress = new Progress(row.workspaceCount(), row.pendingCount(), row.runningCount(),
                row.succeededCount(), row.failedCount(), processed, percent);
        return new JobListItem(row.jobId(), row.status(), row.priority(),
                new CandidatePolicy(row.candidatePolicyId(), row.candidatePolicyCode(), row.candidatePolicyName(),
                        row.candidateVersionId(), row.candidateVersionNo(), row.candidateVersionStatus()),
                progress, row.expiresAt(), !row.expiresAt().isAfter(now), row.rowVersion(),
                row.lastProgressAt(), capabilities(row, now));
    }

    private List<OperationCapability> capabilities(JobRow row, Instant now) {
        boolean expired = !row.expiresAt().isAfter(now);
        boolean mutable = !expired && switch (row.status()) {
            case PENDING, RUNNING, FAILED, READY -> true;
            default -> false;
        };
        return List.of(
                capability("REPRIORITIZE", mutable, expired ? "JOB_EXPIRED" : "STATUS_NOT_MUTABLE"),
                capability("CANCEL", mutable, expired ? "JOB_EXPIRED" : "STATUS_NOT_CANCELLABLE"),
                capability("RETRY_FAILED", !expired && row.status() == GlobalDriftPolicyImpactJobStatus.FAILED,
                        expired ? "JOB_EXPIRED" : "JOB_NOT_FAILED"),
                capability("SEAL", !expired && row.status() == GlobalDriftPolicyImpactJobStatus.READY
                                && row.succeededCount() == row.workspaceCount() && row.failedCount() == 0,
                        expired ? "JOB_EXPIRED" : "JOB_NOT_READY"),
                capability("VIEW_SEALED_SNAPSHOT", row.sealedSnapshotId() != null, "SNAPSHOT_NOT_SEALED"));
    }

    private static OperationCapability capability(String action, boolean enabled, String disabledReason) {
        return new OperationCapability(action, enabled, enabled ? null : disabledReason);
    }

    private ImpactComparison comparison(String snapshotId, String checksum, String document, Instant expiresAt) {
        if (snapshotId == null || document == null) return null;
        try {
            JsonNode root = json.readTree(document);
            JsonNode changes = root.path("parameterChanges"); JsonNode summary = root.path("summary");
            return new ImpactComparison(snapshotId, checksum, expiresAt,
                    new ParameterChanges(changes.path("overdueAfterSecondsDelta").asLong(),
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
                            summary.path("currentSuppressedReports").asLong(),
                            summary.path("candidateSuppressedReports").asLong(),
                            summary.path("newlySuppressedReports").asLong(),
                            summary.path("noLongerSuppressedReports").asLong(),
                            summary.path("currentReminderCandidates").asLong(),
                            summary.path("candidateReminderCandidates").asLong(),
                            summary.path("addedReminderCandidates").asLong(),
                            summary.path("removedReminderCandidates").asLong()),
                    root.path("totalChangedReports").asLong());
        } catch (Exception e) {
            throw new IllegalStateException("Stored Workspace impact evidence is invalid", e);
        }
    }

    private static long stalledSeconds(JobRow row, Instant now) {
        return Math.max(0, Duration.between(row.lastProgressAt(), now).toSeconds());
    }

    private static String recovery(JobRow row, Instant now) {
        return switch (row.status()) {
            case READY -> "READY_FOR_MANUAL_SEAL";
            case FAILED -> "REVIEW_FAILURE_AND_RETRY";
            case SEALED, EXPIRED, CANCELLED -> "NO_ACTION_TERMINAL";
            default -> row.dispatchCount() == 0 ? "START_OR_ENABLE_WORKER"
                    : row.dispatchLeaseUntil() != null && row.dispatchLeaseUntil().isAfter(now)
                    ? "WAIT_FOR_ACTIVE_WORKER" : "REDISPATCH_AFTER_LEASE_EXPIRY";
        };
    }

    private static JobFilter normalize(JobFilter filter) {
        JobFilter value = filter == null ? new JobFilter(Set.of(), Set.of(), null, null, null, null, null) : filter;
        if (value.candidatePolicyId() != null && value.candidatePolicyId() <= 0)
            throw new IllegalArgumentException("candidatePolicyId must be positive");
        if (value.createdFrom() != null && value.createdTo() != null
                && !value.createdFrom().isBefore(value.createdTo()))
            throw new IllegalArgumentException("createdFrom must be before createdTo");
        return new JobFilter(copy(value.statuses()), copy(value.priorities()), value.candidatePolicyId(),
                optional(value.createdBy(), 100, "createdBy"), optional(value.keyword(), 180, "keyword"),
                value.createdFrom(), value.createdTo());
    }

    private static int offset(int page, int size) {
        if (page < 0 || size < 1 || size > 100)
            throw new IllegalArgumentException("page must be non-negative and size must be between 1 and 100");
        long value = (long) page * size;
        if (value > Integer.MAX_VALUE) throw new IllegalArgumentException("page offset is too large");
        return (int) value;
    }
    private static int pages(long total, int size) { return (int) Math.min(Integer.MAX_VALUE, (total + size - 1) / size); }
    private static String required(String value, int max, String field) {
        String normalized = optional(value, max, field);
        if (normalized == null) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static String optional(String value, int max, String field) {
        if (value == null) return null; String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static <T> Set<T> copy(Set<T> values) { return values == null ? Set.of() : Set.copyOf(values); }

    public record JobFilter(Set<GlobalDriftPolicyImpactJobStatus> statuses,
            Set<GlobalImpactJobPriority> priorities, Long candidatePolicyId, String createdBy,
            String keyword, Instant createdFrom, Instant createdTo) {}
    public record JobPage(List<JobListItem> items, int page, int size, long totalElements,
            int totalPages, boolean hasNext) {}
    public record JobListItem(String jobId, GlobalDriftPolicyImpactJobStatus status,
            GlobalImpactJobPriority priority, CandidatePolicy candidatePolicy, Progress progress,
            Instant expiresAt, boolean expired, long rowVersion, Instant lastProgressAt,
            List<OperationCapability> allowedActions) {}
    public record CandidatePolicy(long policyId, String policyCode, String policyName,
            long versionId, int versionNo, String versionStatus) {}
    public record Progress(int workspaceCount, int pendingCount, int runningCount,
            int succeededCount, int failedCount, int processedCount, int progressPercent) {}
    public record OperationCapability(String action, boolean enabled, String disabledReasonCode) {}
    public record JobDetail(JobListItem summary, String candidateChecksum, String coverageChecksum,
            Instant snapshotAt, String sealedSnapshotId, long dispatchCount, Instant lastDispatchedAt,
            Instant lastProgressAt, String dispatchLeaseOwner, Instant dispatchLeaseUntil,
            long stalledSeconds, String recoveryRecommendation,
            String createdBy, Instant createdAt, String updatedBy, Instant updatedAt) {}
    public record WorkspaceImpactPage(String jobId, List<WorkspaceImpactItem> items, int page, int size,
            long totalElements, int totalPages, boolean hasNext) {}
    public record WorkspaceImpactSummary(String jobId, long workspaceCount,
            RiskDistribution risks, StatusDistribution statuses, ImpactTotals impactTotals) {}
    public record RiskDistribution(long low, long medium, long high, long critical) {}
    public record StatusDistribution(long pending, long running, long succeeded, long failed) {}
    public record ImpactTotals(long changedReports, long newlyOverdueReports, long addedReminderCandidates) {}
    public record WorkspaceImpactItem(long workspaceId, String workspaceCode, String workspaceName,
            String environmentCode, WorkspaceRiskLevel riskLevel, int itemOrder, CurrentPolicy currentPolicy,
            GlobalDriftPolicyImpactJobItemStatus status, int attemptCount,
            String leaseOwner, Instant leaseUntil, String failureCode, String failureMessage,
            Instant startedAt, Instant finishedAt, ImpactComparison impactComparison) {}
    public record CurrentPolicy(Long policyId, String policyCode, String policyName,
            Long versionId, Integer versionNo, String checksum) {}
    public record ImpactComparison(String snapshotId, String impactChecksum, Instant expiresAt,
            ParameterChanges parameterChanges, ImpactSummary summary, long totalChangedReports) {}
    public record ParameterChanges(long overdueAfterSecondsDelta, long aggregationWindowSecondsDelta,
            long reminderIntervalSecondsDelta, int maximumRemindersDelta, boolean ownerChanged,
            boolean suppressedDriftKindsChanged, boolean suppressedCheckCodesChanged) {}
    public record ImpactSummary(long actionableReports, long currentOverdueReports,
            long candidateOverdueReports, long newlyOverdueReports, long noLongerOverdueReports,
            long currentSuppressedReports, long candidateSuppressedReports, long newlySuppressedReports,
            long noLongerSuppressedReports, long currentReminderCandidates,
            long candidateReminderCandidates, long addedReminderCandidates, long removedReminderCandidates) {}
    public record Timeline(String jobId, List<TimelineEntry> items, boolean truncated) {}
    public record TimelineEntry(int sequence, String eventId, String eventType, String actorCode,
            String summary, String detail, Instant occurredAt) {}
}
