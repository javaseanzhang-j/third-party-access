package com.ftk.tpip.release.domain.model;

public record DriftGovernanceReminderBatchMember(long batchId, long executionId, int reminderNo,
        String evaluationChecksum) {
    public DriftGovernanceReminderBatchMember {
        if (batchId < 0 || executionId <= 0 || reminderNo < 1 || reminderNo > 100)
            throw new IllegalArgumentException("reminder batch member identity is invalid");
        if (evaluationChecksum == null || !evaluationChecksum.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("evaluationChecksum must be lowercase SHA-256");
    }
}
