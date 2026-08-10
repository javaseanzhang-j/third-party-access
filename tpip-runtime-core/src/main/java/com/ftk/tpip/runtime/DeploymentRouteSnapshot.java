package com.ftk.tpip.runtime;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record DeploymentRouteSnapshot(String operationCode, String environmentCode,
                                      String revision, List<WeightedDeploymentTarget> targets) {
    public DeploymentRouteSnapshot {
        operationCode = required(operationCode, "operationCode");
        environmentCode = required(environmentCode, "environmentCode");
        revision = required(revision, "revision");
        targets = List.copyOf(Objects.requireNonNull(targets, "targets must not be null"));
        if (targets.isEmpty() || targets.size() > 2) {
            throw new IllegalArgumentException("A route must contain one or two active deployments");
        }
        BigDecimal total = targets.stream().map(WeightedDeploymentTarget::trafficPercentage)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
        if (total.compareTo(new BigDecimal("100.00")) != 0) {
            throw new IllegalArgumentException("Route traffic must total 100.00");
        }
        if (new HashSet<>(targets.stream().map(WeightedDeploymentTarget::deploymentId).toList()).size() != targets.size()) {
            throw new IllegalArgumentException("Route contains duplicate deploymentId");
        }
    }
    private static String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
