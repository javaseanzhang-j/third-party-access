package com.ftk.tpip.control.application.release;

import com.ftk.tpip.release.domain.model.*;
import com.ftk.tpip.release.domain.repository.DriftGovernanceReminderBatchRepository;
import com.ftk.tpip.release.domain.repository.DriftGovernanceReminderObservabilityRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DriftGovernanceReminderObservabilityService {
    private final DriftGovernanceReminderBatchRepository batches;
    private final DriftGovernanceReminderObservabilityRepository observability;

    public DriftGovernanceReminderObservabilityService(DriftGovernanceReminderBatchRepository batches,
            DriftGovernanceReminderObservabilityRepository observability) {
        this.batches = batches; this.observability = observability;
    }

    @Transactional(readOnly = true)
    public BatchDiff diff(long batchId) {
        DriftGovernanceReminderBatch requested = batch(batchId);
        DriftGovernanceReminderBatch from;
        DriftGovernanceReminderBatch to;
        if (requested.replacesBatchId() != null) {
            from = batch(requested.replacesBatchId()); to = requested;
        } else if (requested.replacedByBatchId() != null) {
            from = requested; to = batch(requested.replacedByBatchId());
        } else {
            throw new IllegalArgumentException("Reminder batch does not have replacement lineage");
        }
        if (!Objects.equals(from.replacedByBatchId(), to.id()) || !Objects.equals(to.replacesBatchId(), from.id()))
            throw new IllegalStateException("Reminder batch replacement lineage is inconsistent");
        Map<Long, DriftGovernanceReminderBatchMember> before = members(from.id());
        Map<Long, DriftGovernanceReminderBatchMember> after = members(to.id());
        var ids = new TreeSet<Long>(); ids.addAll(before.keySet()); ids.addAll(after.keySet());
        int added = 0; int removed = 0; int unchanged = 0; int modified = 0;
        List<MemberChange> changes = new ArrayList<>();
        for (Long id : ids) {
            var oldValue = before.get(id); var newValue = after.get(id);
            DriftGovernanceReminderMemberChangeKind kind;
            if (oldValue == null) { kind = DriftGovernanceReminderMemberChangeKind.ADDED; added++; }
            else if (newValue == null) { kind = DriftGovernanceReminderMemberChangeKind.REMOVED; removed++; }
            else if (oldValue.reminderNo() == newValue.reminderNo()
                    && oldValue.evaluationChecksum().equals(newValue.evaluationChecksum())) {
                kind = DriftGovernanceReminderMemberChangeKind.UNCHANGED; unchanged++;
            } else { kind = DriftGovernanceReminderMemberChangeKind.MODIFIED; modified++; }
            changes.add(new MemberChange(id, kind, oldValue == null ? null : oldValue.reminderNo(),
                    newValue == null ? null : newValue.reminderNo(),
                    oldValue == null ? null : oldValue.evaluationChecksum(),
                    newValue == null ? null : newValue.evaluationChecksum()));
        }
        return new BatchDiff(from.id(), to.id(), added, removed, unchanged, modified, List.copyOf(changes));
    }

    @Transactional(readOnly = true)
    public DeliveryStatus delivery(long batchId) {
        var batch = batch(batchId);
        if (batch.outboxId() == null) return new DeliveryStatus(batch.id(), false, null);
        var overview = observability.findDeliveryOverview(batch.outboxId()).orElseThrow(() ->
                new IllegalStateException("Reminder batch outbox reference is missing"));
        return new DeliveryStatus(batch.id(), true, overview);
    }

    @Transactional(readOnly = true)
    public BatchTimeline timeline(long batchId) {
        var value = batch(batchId);
        return new BatchTimeline(value.id(), value.batchCode(), value.rowVersion(),
                observability.findBatchTimeline(value.batchCode(), 100));
    }

    @Transactional(readOnly = true)
    public ReminderMetrics metrics(long workspaceId) {
        if (workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        var value = observability.summarize(workspaceId, Instant.now());
        long terminal = value.deliveredDeliveries() + value.deadLetterDeliveries();
        BigDecimal rate = terminal == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(
                value.deliveredDeliveries()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(terminal), 2, RoundingMode.HALF_UP);
        return new ReminderMetrics(value.totalBatches(), value.draftBatches(), value.approvedBatches(),
                value.dispatchedBatches(), value.cancelledBatches(), value.dueUnbatchedExecutions(),
                value.activeReservedExecutions(), value.oldestDueAt(), value.unroutedOutboxes(),
                value.routedOutboxes(), value.noMatchOutboxes(), value.renderFailedOutboxes(),
                value.pendingDeliveries(), value.claimedDeliveries(), value.deliveredDeliveries(),
                value.deadLetterDeliveries(), rate);
    }

    private DriftGovernanceReminderBatch batch(long id) {
        if (id <= 0) throw new IllegalArgumentException("batchId must be positive");
        return batches.findById(id).orElseThrow(() ->
                new IllegalArgumentException("DriftGovernanceReminderBatch does not exist: " + id));
    }
    private Map<Long, DriftGovernanceReminderBatchMember> members(long id) {
        var result = new HashMap<Long, DriftGovernanceReminderBatchMember>();
        batches.findMembers(id).forEach(value -> result.put(value.executionId(), value));
        return result;
    }

    public record MemberChange(long executionId, DriftGovernanceReminderMemberChangeKind changeKind,
            Integer previousReminderNo, Integer currentReminderNo, String previousEvaluationChecksum,
            String currentEvaluationChecksum) {}
    public record BatchDiff(long fromBatchId, long toBatchId, int addedCount, int removedCount,
            int unchangedCount, int modifiedCount, List<MemberChange> members) {}
    public record DeliveryStatus(long batchId, boolean submitted,
            DriftGovernanceReminderObservabilityRepository.DeliveryOverview overview) {}
    public record BatchTimeline(long batchId, String batchCode, long currentRowVersion,
            List<DriftGovernanceReminderObservabilityRepository.BatchAuditEvent> events) {}
    public record ReminderMetrics(long totalBatches, long draftBatches, long approvedBatches,
            long dispatchedBatches, long cancelledBatches, long dueUnbatchedExecutions,
            long activeReservedExecutions, Instant oldestDueAt, long unroutedOutboxes, long routedOutboxes,
            long noMatchOutboxes, long renderFailedOutboxes, long pendingDeliveries, long claimedDeliveries,
            long deliveredDeliveries, long deadLetterDeliveries, BigDecimal terminalDeliverySuccessRate) {}
}
