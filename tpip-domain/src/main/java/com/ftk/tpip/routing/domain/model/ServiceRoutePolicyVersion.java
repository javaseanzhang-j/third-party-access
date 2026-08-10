package com.ftk.tpip.routing.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ServiceRoutePolicyVersion(Long id, long policyId, int versionNo, boolean healthFilterEnabled,
        RouteFallbackMode fallbackMode, String contentChecksum, RouteLifecycleStatus lifecycleStatus,
        Instant publishedAt, Instant createdAt, List<ServiceRouteTarget> targets) {
    public ServiceRoutePolicyVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (policyId <= 0 || versionNo < 0) throw new IllegalArgumentException("policyId/versionNo is invalid");
        fallbackMode = Objects.requireNonNull(fallbackMode);
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus);
        targets = List.copyOf(Objects.requireNonNull(targets));
        if (targets.isEmpty()) throw new IllegalArgumentException("route version requires at least one target");
        if (targets.stream().map(ServiceRouteTarget::bindingId).distinct().count() != targets.size()) throw new IllegalArgumentException("route version contains duplicate bindingId");
    }
}
