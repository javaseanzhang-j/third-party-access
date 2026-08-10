package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.Objects;

public record DriftGovernanceExecution(Long id, long driftReportId, long workspaceId, String policySource,
        Long policyId, Long policyVersionId, String aggregationKey, String ownerCode,
        DriftGovernanceExecutionStatus status, int maximumReminders, long reminderIntervalSeconds,
        int reminderCount, Instant nextReminderAt,
        String policySnapshotDocument, String evaluationDocument, String evaluationChecksum, long rowVersion,
        String materializedBy, Instant materializedAt, Instant updatedAt) {
    public DriftGovernanceExecution {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (driftReportId <= 0 || workspaceId <= 0)
            throw new IllegalArgumentException("execution references must be positive");
        if (!isSource(policySource)) throw new IllegalArgumentException("policySource is invalid");
        boolean builtIn = "BUILT_IN_DEFAULT".equals(policySource);
        if (builtIn != (policyId == null && policyVersionId == null)
                || (!builtIn && (policyId == null || policyVersionId == null)))
            throw new IllegalArgumentException("policy references do not match policySource");
        sha256(aggregationKey, "aggregationKey");
        if (ownerCode == null || ownerCode.isBlank() || ownerCode.trim().length() > 100)
            throw new IllegalArgumentException("ownerCode is invalid");
        ownerCode = ownerCode.trim();
        Objects.requireNonNull(status, "status must not be null");
        if (maximumReminders < 1 || maximumReminders > 100 || reminderCount < 0
                || reminderCount > maximumReminders)
            throw new IllegalArgumentException("reminder budget is invalid");
        if (reminderIntervalSeconds < 3600 || reminderIntervalSeconds > 2592000)
            throw new IllegalArgumentException("reminderIntervalSeconds is invalid");
        Objects.requireNonNull(nextReminderAt, "nextReminderAt must not be null");
        json(policySnapshotDocument, "policySnapshotDocument");
        json(evaluationDocument, "evaluationDocument");
        sha256(evaluationChecksum, "evaluationChecksum");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
        if (materializedBy == null || materializedBy.isBlank() || materializedBy.trim().length() > 100)
            throw new IllegalArgumentException("materializedBy is invalid");
        materializedBy = materializedBy.trim();
    }

    private static boolean isSource(String value) {
        return "BUILT_IN_DEFAULT".equals(value) || "GLOBAL_POLICY".equals(value)
                || "WORKSPACE_POLICY".equals(value);
    }
    private static void sha256(String value, String field) {
        if (value == null || !value.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(field + " must be lowercase SHA-256");
    }
    private static void json(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 1_000_000)
            throw new IllegalArgumentException(field + " is invalid");
    }
}
