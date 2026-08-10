package com.ftk.tpip.bundle;

import com.ftk.tpip.routing.domain.model.RouteFallbackMode;
import java.util.List;
import java.util.Objects;

/** Published service route version frozen into a deployment Bundle. */
public record CompiledServiceRoutePlan(
        long policyId,
        long routeVersionId,
        int versionNo,
        boolean healthFilterEnabled,
        RouteFallbackMode fallbackMode,
        String contentChecksum,
        List<CompiledServiceRouteTarget> targets) {

    public CompiledServiceRoutePlan {
        if (policyId <= 0 || routeVersionId <= 0 || versionNo <= 0)
            throw new IllegalArgumentException("route policy identity is invalid");
        fallbackMode = Objects.requireNonNull(fallbackMode);
        contentChecksum = Objects.requireNonNull(contentChecksum, "contentChecksum must not be null");
        targets = List.copyOf(Objects.requireNonNull(targets));
        if (targets.isEmpty()) throw new IllegalArgumentException("compiled route requires targets");
        if (targets.stream().map(CompiledServiceRouteTarget::bindingId).distinct().count() != targets.size())
            throw new IllegalArgumentException("compiled route contains duplicate bindingId");
    }
}
