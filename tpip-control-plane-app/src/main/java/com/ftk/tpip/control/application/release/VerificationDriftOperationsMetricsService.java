package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.repository.VerificationDriftOperationsMetricsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationDriftOperationsMetricsService {
    private final VerificationDriftOperationsMetricsRepository repository;
    private final DriftGovernancePolicyResolver policies;

    public VerificationDriftOperationsMetricsService(VerificationDriftOperationsMetricsRepository repository,
            DriftGovernancePolicyResolver policies) {
        this.repository = repository;
        this.policies = policies;
    }

    @Transactional(readOnly = true)
    public Metrics metrics(long workspaceId, int windowDays, Duration slaOverride) {
        return metrics(workspaceId, windowDays, slaOverride, Instant.now());
    }

    Metrics metrics(long workspaceId, int windowDays, Duration slaOverride, Instant now) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        if (windowDays < 1 || windowDays > 90)
            throw new IllegalArgumentException("windowDays must be between 1 and 90");
        if (slaOverride != null && (slaOverride.compareTo(Duration.ofHours(1)) < 0
                || slaOverride.compareTo(Duration.ofDays(365)) > 0))
            throw new IllegalArgumentException("sla must be between 1 hour and 365 days");
        var policy = policies.resolve(workspaceId);
        Duration sla = slaOverride == null ? policy.overdueAfter() : slaOverride;
        if (sla.compareTo(Duration.ofHours(1)) < 0 || sla.compareTo(Duration.ofDays(365)) > 0)
            throw new IllegalStateException("resolved governance policy SLA is outside the supported range");
        SlaMode slaMode = slaOverride == null ? SlaMode.POLICY : SlaMode.EXPLICIT_ANALYSIS_OVERRIDE;
        LocalDate today = now.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate firstDate = today.minusDays(windowDays - 1L);
        Instant windowStart = firstDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        var query = new VerificationDriftOperationsMetricsRepository.Query(workspaceId, windowStart, now,
                now.minus(sla));
        var snapshot = repository.snapshot(query);
        var resolutions = repository.resolutions(query);
        var operations = repository.operations(query);
        List<AssigneeWorkload> assignees = repository.assignees(query).stream().map(value -> new AssigneeWorkload(
                value.assigneeCode(), value.assigneeCode() != null, value.actionableReports(), value.overdueReports(),
                value.oldestActionableAt(), ageHours(value.oldestActionableAt(), now))).toList();
        Map<LocalDate, VerificationDriftOperationsMetricsRepository.DailyRow> rows = repository.daily(query).stream()
                .collect(Collectors.toMap(VerificationDriftOperationsMetricsRepository.DailyRow::date,
                        Function.identity()));
        List<DailyMetric> daily = IntStream.range(0, windowDays).mapToObj(firstDate::plusDays).map(date -> {
            var value = rows.get(date);
            return value == null ? new DailyMetric(date, 0, 0, 0, 0, 0)
                    : new DailyMetric(date, value.createdReports(), value.resolvedReports(),
                            value.previewedCommands(), value.appliedCommands(), value.rejectedCommands());
        }).toList();
        long withinSla = snapshot.actionableReports() - snapshot.overdueReports();
        return new Metrics(workspaceId, now, windowStart, windowDays, sla.toHours(), sla.toSeconds(),
                new SlaEvidence(slaMode, policy.source(), policy.policyId(), policy.policyVersionId(),
                        policy.overdueAfter().toSeconds(), sla.toSeconds(), policy.ownerCode()),
                new Backlog(snapshot.actionableReports(), withinSla, snapshot.overdueReports(),
                        snapshot.assignedActionableReports(),
                        snapshot.actionableReports() - snapshot.assignedActionableReports(),
                        snapshot.unassignedOverdueReports(), percent(snapshot.overdueReports(), snapshot.actionableReports()),
                        percent(snapshot.assignedActionableReports(), snapshot.actionableReports()),
                        snapshot.oldestActionableAt(), ageHours(snapshot.oldestActionableAt(), now),
                        decimal(snapshot.averageAgeHours())),
                new Resolution(resolutions.resolvedReports(), resolutions.resolvedWithinSla(),
                        resolutions.resolvedReports() - resolutions.resolvedWithinSla(), resolutions.acceptedReports(),
                        resolutions.dismissedReports(), percent(resolutions.resolvedWithinSla(), resolutions.resolvedReports()),
                        decimal(resolutions.averageResolutionHours()), decimal(resolutions.maximumResolutionHours())),
                new Commands(operations.commands(), operations.previewedCommands(), operations.appliedCommands(),
                        operations.rejectedCommands(), percent(operations.appliedCommands(),
                                operations.appliedCommands() + operations.rejectedCommands()),
                        operations.requestedItems(), operations.eligibleItems(), operations.appliedItems(),
                        operations.rejectedItems()), assignees, daily);
    }

    private static long ageHours(Instant value, Instant now) {
        return value == null ? 0 : Math.max(0, Duration.between(value, now).toHours());
    }

    private static BigDecimal percent(long numerator, long denominator) {
        if (denominator == 0) return BigDecimal.ZERO.setScale(2);
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    public enum SlaMode { POLICY, EXPLICIT_ANALYSIS_OVERRIDE }
    public record SlaEvidence(SlaMode mode, DriftGovernancePolicyApplicationService.ResolutionSource source,
            Long policyId, Long policyVersionId, long configuredSlaSeconds, long effectiveSlaSeconds,
            String ownerCode) {}
    public record Metrics(long workspaceId, Instant snapshotAt, Instant windowStart, int windowDays,
            long slaHours, long slaSeconds, SlaEvidence slaPolicy, Backlog backlog, Resolution resolution,
            Commands commands, List<AssigneeWorkload> assignees, List<DailyMetric> daily) {}
    public record Backlog(long actionableReports, long withinSlaReports, long breachedReports,
            long assignedReports, long unassignedReports, long unassignedBreachedReports,
            BigDecimal breachRatePercent, BigDecimal assignmentCoveragePercent,
            Instant oldestActionableAt, long oldestAgeHours, BigDecimal averageAgeHours) {}
    public record Resolution(long resolvedReports, long withinSlaReports, long breachedReports,
            long acceptedReports, long dismissedReports, BigDecimal slaCompliancePercent,
            BigDecimal averageResolutionHours, BigDecimal maximumResolutionHours) {}
    public record Commands(long commands, long previewedCommands, long appliedCommands, long rejectedCommands,
            BigDecimal executionSuccessPercent, long requestedItems, long eligibleItems,
            long appliedItems, long rejectedItems) {}
    public record AssigneeWorkload(String assigneeCode, boolean assigned, long actionableReports,
            long overdueReports, Instant oldestActionableAt, long oldestAgeHours) {}
    public record DailyMetric(LocalDate date, long createdReports, long resolvedReports,
            long previewedCommands, long appliedCommands, long rejectedCommands) {}
}
