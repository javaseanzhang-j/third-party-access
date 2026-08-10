package com.ftk.tpip.release.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record IntegrationDeployment(
        Long id,
        AssetCode deploymentCode,
        long bundleId,
        long operationId,
        String environmentCode,
        DeploymentStatus deploymentStatus,
        BigDecimal trafficPercentage,
        Long previousDeploymentId,
        String instanceStatus,
        String preheatEvidence,
        String rolloutMetadata,
        long rowVersion,
        String deployedBy,
        Instant deployedAt,
        Instant activatedAt,
        Instant endedAt,
        Instant updatedAt) {
    private static final Pattern ENVIRONMENT = Pattern.compile("^[a-z][a-z0-9_-]*$");

    public IntegrationDeployment {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        Objects.requireNonNull(deploymentCode, "deploymentCode must not be null");
        if (bundleId <= 0 || operationId <= 0) throw new IllegalArgumentException("deployment references must be positive");
        environmentCode = required(environmentCode, "environmentCode");
        if (!ENVIRONMENT.matcher(environmentCode).matches()) throw new IllegalArgumentException("Invalid environmentCode");
        Objects.requireNonNull(deploymentStatus, "deploymentStatus must not be null");
        trafficPercentage = Objects.requireNonNull(trafficPercentage, "trafficPercentage must not be null")
                .setScale(2, RoundingMode.UNNECESSARY);
        if (trafficPercentage.signum() < 0 || trafficPercentage.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("trafficPercentage must be between 0 and 100");
        }
        if (deploymentStatus == DeploymentStatus.ACTIVE && trafficPercentage.signum() <= 0) {
            throw new IllegalArgumentException("ACTIVE deployment requires positive traffic");
        }
        if (deploymentStatus != DeploymentStatus.ACTIVE && trafficPercentage.signum() != 0) {
            throw new IllegalArgumentException("Only ACTIVE deployment may carry traffic");
        }
        if (previousDeploymentId != null && previousDeploymentId <= 0) {
            throw new IllegalArgumentException("previousDeploymentId must be positive");
        }
        instanceStatus = json(instanceStatus);
        preheatEvidence = json(preheatEvidence);
        rolloutMetadata = json(rolloutMetadata);
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
        deployedBy = required(deployedBy, "deployedBy");
        if (deploymentStatus == DeploymentStatus.ACTIVE && activatedAt == null) {
            throw new IllegalArgumentException("ACTIVE deployment requires activatedAt");
        }
        if ((deploymentStatus == DeploymentStatus.DEPRECATED || deploymentStatus == DeploymentStatus.ROLLED_BACK)
                && endedAt == null) throw new IllegalArgumentException("Ended deployment requires endedAt");
    }

    public static IntegrationDeployment pending(AssetCode code, long bundleId, long operationId,
            String environmentCode, Long previousDeploymentId, String rolloutMetadata, String actor) {
        return new IntegrationDeployment(null, code, bundleId, operationId, environmentCode,
                DeploymentStatus.PENDING, new BigDecimal("0.00"), previousDeploymentId,
                "{}", "{}", rolloutMetadata, 0, actor, null, null, null, null);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
    private static String json(String value) { return value == null || value.isBlank() ? "{}" : value; }
}
