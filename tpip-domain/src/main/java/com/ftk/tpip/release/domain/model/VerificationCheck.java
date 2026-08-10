package com.ftk.tpip.release.domain.model;

import java.time.Instant;
import java.util.Objects;

public record VerificationCheck(Long id, long verificationRunId, String checkCode, String checkName,
        VerificationCheckStatus status, String resultDetails, String evidenceDocument,
        Instant startedAt, Instant finishedAt) {
    public VerificationCheck {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (verificationRunId <= 0) throw new IllegalArgumentException("verificationRunId must be positive");
        checkCode = required(checkCode, "checkCode", 200);
        checkName = required(checkName, "checkName", 300);
        Objects.requireNonNull(status, "status must not be null");
        resultDetails = resultDetails == null ? "{}" : resultDetails;
        evidenceDocument = evidenceDocument == null ? "{}" : evidenceDocument;
    }

    private static String required(String value, String field, int max) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(field + " is too long");
        return normalized;
    }
}
