package com.ftk.tpip.runtime;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

public record WeightedDeploymentTarget(long deploymentId, String deploymentCode,
                                       BundleCoordinate bundle, BigDecimal trafficPercentage) {
    public WeightedDeploymentTarget {
        if (deploymentId <= 0) throw new IllegalArgumentException("deploymentId must be positive");
        if (deploymentCode == null || deploymentCode.isBlank()) throw new IllegalArgumentException("deploymentCode is blank");
        Objects.requireNonNull(bundle, "bundle must not be null");
        trafficPercentage = Objects.requireNonNull(trafficPercentage).setScale(2, RoundingMode.UNNECESSARY);
        if (trafficPercentage.signum() <= 0 || trafficPercentage.compareTo(new BigDecimal("100.00")) > 0) {
            throw new IllegalArgumentException("trafficPercentage must be between 0.01 and 100.00");
        }
    }
}
