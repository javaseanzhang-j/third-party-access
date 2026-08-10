package com.ftk.tpip.routing.domain.model;

import java.util.Map;
import java.util.Objects;

public record ServiceRouteTarget(Long id, long routeVersionId, long bindingId, boolean enabled, int priority,
        int weight, RouteHealthRequirement healthRequirement, RouteManualStatus manualStatus,
        Map<String, Object> conditions) {
    public ServiceRouteTarget {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (routeVersionId < 0 || bindingId <= 0) throw new IllegalArgumentException("routeVersionId/bindingId is invalid");
        if (priority < 0 || priority > 10000) throw new IllegalArgumentException("priority must be between 0 and 10000");
        if (weight < 1 || weight > 10000) throw new IllegalArgumentException("weight must be between 1 and 10000");
        healthRequirement = Objects.requireNonNull(healthRequirement);
        manualStatus = Objects.requireNonNull(manualStatus);
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
    }
}
