package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.Objects;

public record VerificationDriftReview(long driftReportId, VerificationDriftReviewStatus status, long rowVersion,
        String assigneeCode, String assignedBy, Instant assignedAt, String assignmentNote,
        String acknowledgedBy, Instant acknowledgedAt, String acknowledgmentNote,
        String resolvedBy, Instant resolvedAt, String resolutionReason, Long successorBaselineId,
        Instant createdAt, Instant updatedAt) {
    public VerificationDriftReview {
        if (driftReportId <= 0 || rowVersion < 0) throw new IllegalArgumentException("invalid drift review identity");
        Objects.requireNonNull(status, "status must not be null");
        boolean assigned = assigneeCode != null && assignedBy != null && assignedAt != null && assignmentNote != null;
        boolean anyAssignment = assigneeCode != null || assignedBy != null || assignedAt != null || assignmentNote != null;
        if (anyAssignment != assigned) throw new IllegalArgumentException("drift review assignment is incomplete");
        boolean acknowledged = acknowledgedBy != null && acknowledgedAt != null && acknowledgmentNote != null;
        boolean resolved = resolvedBy != null && resolvedAt != null && resolutionReason != null;
        if (status == VerificationDriftReviewStatus.OPEN && (acknowledged || resolved || successorBaselineId != null))
            throw new IllegalArgumentException("OPEN review cannot contain decisions");
        if (status == VerificationDriftReviewStatus.ACKNOWLEDGED && (!acknowledged || resolved || successorBaselineId != null))
            throw new IllegalArgumentException("ACKNOWLEDGED review is inconsistent");
        if ((status == VerificationDriftReviewStatus.ACCEPTED || status == VerificationDriftReviewStatus.DISMISSED)
                && (!acknowledged || !resolved)) throw new IllegalArgumentException("resolved review is incomplete");
        if (status == VerificationDriftReviewStatus.ACCEPTED && (successorBaselineId == null || successorBaselineId <= 0))
            throw new IllegalArgumentException("ACCEPTED review requires successor baseline");
        if (status == VerificationDriftReviewStatus.DISMISSED && successorBaselineId != null)
            throw new IllegalArgumentException("DISMISSED review cannot reference successor baseline");
    }

    public VerificationDriftReview(long driftReportId, VerificationDriftReviewStatus status, long rowVersion,
            String acknowledgedBy, Instant acknowledgedAt, String acknowledgmentNote,
            String resolvedBy, Instant resolvedAt, String resolutionReason, Long successorBaselineId,
            Instant createdAt, Instant updatedAt) {
        this(driftReportId, status, rowVersion, null, null, null, null, acknowledgedBy, acknowledgedAt,
                acknowledgmentNote, resolvedBy, resolvedAt, resolutionReason, successorBaselineId, createdAt, updatedAt);
    }
}
