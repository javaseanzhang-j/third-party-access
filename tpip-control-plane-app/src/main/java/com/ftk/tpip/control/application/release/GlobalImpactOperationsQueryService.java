package com.ftk.tpip.control.application.release;

import com.ftk.tpip.control.configuration.GlobalImpactSchedulingProperties;
import com.ftk.tpip.release.domain.model.GlobalDriftPolicyImpactJobStatus;
import com.ftk.tpip.release.domain.model.GlobalImpactJobPriority;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository.JobRow;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GlobalImpactOperationsQueryService {
    private final GlobalImpactJobQueryRepository queries;
    private final GlobalImpactSchedulingProperties properties;

    public GlobalImpactOperationsQueryService(GlobalImpactJobQueryRepository queries,
            GlobalImpactSchedulingProperties properties) {
        this.queries = queries;
        this.properties = properties;
        properties.validate();
    }

    @Transactional(readOnly = true)
    public OperationsOverview overview(int limit) {
        if (limit < 1 || limit > 500) throw new IllegalArgumentException("limit must be between 1 and 500");
        Instant now = Instant.now();
        var rows = queries.findStalledJobs(now, now.minus(properties.getStallThreshold()), limit);
        var items = rows.stream().map(row -> item(row, now)).toList();
        long critical = items.stream().filter(item -> item.severity() == StallSeverity.CRITICAL).count();
        var grouped = new LinkedHashMap<String, Long>();
        items.forEach(item -> grouped.merge(item.recoveryRecommendation(), 1L, Long::sum));
        var recommendations = grouped.entrySet().stream()
                .map(entry -> new RecoveryCount(entry.getKey(), entry.getValue())).toList();
        return new OperationsOverview(now, properties.getStallThreshold().toSeconds(),
                properties.getCriticalStallThreshold().toSeconds(), items.size(),
                critical, items.size() - critical, recommendations, items, items.size() == limit);
    }

    private OperationsItem item(JobRow row, Instant now) {
        long stalled = Math.max(0, Duration.between(row.lastProgressAt(), now).toSeconds());
        StallSeverity severity = stalled >= properties.getCriticalStallThreshold().toSeconds()
                ? StallSeverity.CRITICAL : StallSeverity.WARNING;
        int processed = row.succeededCount() + row.failedCount();
        int percent = row.workspaceCount() == 0 ? 100
                : (int) Math.min(100, Math.round(processed * 100.0 / row.workspaceCount()));
        return new OperationsItem(row.jobId(), row.status(), row.priority(),
                new CandidatePolicy(row.candidatePolicyId(), row.candidatePolicyCode(), row.candidatePolicyName(),
                        row.candidateVersionId(), row.candidateVersionNo()),
                new Progress(row.workspaceCount(), row.pendingCount(), row.runningCount(), row.succeededCount(),
                        row.failedCount(), percent), row.expiresAt(), !row.expiresAt().isAfter(now), row.rowVersion(),
                row.dispatchCount(), row.lastDispatchedAt(), row.lastProgressAt(), row.dispatchLeaseOwner(),
                row.dispatchLeaseUntil(), stalled, severity, recovery(row, now));
    }

    private static String recovery(JobRow row, Instant now) {
        return row.dispatchCount() == 0 ? "START_OR_ENABLE_WORKER"
                : row.dispatchLeaseUntil() != null && row.dispatchLeaseUntil().isAfter(now)
                ? "WAIT_FOR_ACTIVE_WORKER" : "REDISPATCH_AFTER_LEASE_EXPIRY";
    }

    public enum StallSeverity { WARNING, CRITICAL }
    public record OperationsOverview(Instant generatedAt, long stallThresholdSeconds,
            long criticalThresholdSeconds, long stalledCount, long criticalCount, long warningCount,
            List<RecoveryCount> recoveryRecommendations, List<OperationsItem> items, boolean limitReached) {}
    public record RecoveryCount(String recommendation, long count) {}
    public record OperationsItem(String jobId, GlobalDriftPolicyImpactJobStatus status,
            GlobalImpactJobPriority priority, CandidatePolicy candidatePolicy, Progress progress,
            Instant expiresAt, boolean expired, long rowVersion, long dispatchCount,
            Instant lastDispatchedAt, Instant lastProgressAt, String dispatchLeaseOwner,
            Instant dispatchLeaseUntil, long stalledSeconds, StallSeverity severity,
            String recoveryRecommendation) {}
    public record CandidatePolicy(long policyId, String policyCode, String policyName,
            long versionId, int versionNo) {}
    public record Progress(int workspaceCount, int pendingCount, int runningCount,
            int succeededCount, int failedCount, int progressPercent) {}
}
