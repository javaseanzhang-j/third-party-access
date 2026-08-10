package com.ftk.tpip.release.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record DeploymentHealthEvaluation(
        Long id,
        long deploymentId,
        Instant windowStart,
        Instant windowEnd,
        long sampleCount,
        long failureCount,
        BigDecimal errorRatePercentage,
        long p95LatencyMs,
        DeploymentHealthDecision decision,
        DeploymentHealthAction action,
        Long rollbackDeploymentId,
        String evidence,
        String evaluatedBy,
        Instant createdAt) {
    public DeploymentHealthEvaluation {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (deploymentId <= 0) throw new IllegalArgumentException("deploymentId must be positive");
        Objects.requireNonNull(windowStart, "windowStart must not be null");
        Objects.requireNonNull(windowEnd, "windowEnd must not be null");
        if (!windowEnd.isAfter(windowStart)) throw new IllegalArgumentException("windowEnd must be after windowStart");
        if (sampleCount < 0 || failureCount < 0 || failureCount > sampleCount) {
            throw new IllegalArgumentException("sample and failure counts are invalid");
        }
        errorRatePercentage = Objects.requireNonNull(errorRatePercentage, "errorRatePercentage must not be null");
        if (errorRatePercentage.signum() < 0 || errorRatePercentage.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("errorRatePercentage must be between 0 and 100");
        }
        if (p95LatencyMs < 0) throw new IllegalArgumentException("p95LatencyMs must not be negative");
        decision = Objects.requireNonNull(decision, "decision must not be null");
        action = Objects.requireNonNull(action, "action must not be null");
        if (rollbackDeploymentId != null && rollbackDeploymentId <= 0) {
            throw new IllegalArgumentException("rollbackDeploymentId must be positive");
        }
        evidence = evidence == null || evidence.isBlank() ? "{}" : evidence;
        evaluatedBy = Objects.requireNonNull(evaluatedBy, "evaluatedBy must not be null");
    }
}
