package com.ftk.tpip.release.domain.repository;

import com.ftk.tpip.release.domain.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DriftGovernanceReminderBatchRepository {
    DriftGovernanceReminderBatch create(DriftGovernanceReminderBatch batch,
            List<DriftGovernanceReminderBatchMember> members, String actor);
    Optional<DriftGovernanceReminderBatch> findById(long id);
    List<DriftGovernanceReminderBatch> findByWorkspaceId(long workspaceId);
    List<DriftGovernanceReminderBatchMember> findMembers(long batchId);
    DriftGovernanceReminderBatch approve(long id, long expectedRowVersion, String actor, Instant now);
    DriftGovernanceReminderBatch lockForUpdate(long id);
    DriftGovernanceReminderBatch cancel(long id, long expectedRowVersion, String reason, String actor,
            Instant now, Long replacedByBatchId);
    DriftGovernanceReminderBatch linkReplacement(long id, long expectedRowVersion, long replacedByBatchId,
            String actor);
    DriftGovernanceReminderBatch markDispatched(long id, long expectedRowVersion, long outboxId,
            List<ReminderProgress> progress, String actor, Instant now);

    record ReminderProgress(long executionId, int expectedReminderCount, int reminderCount,
            DriftGovernanceExecutionStatus status, Instant nextReminderAt) {}
}
