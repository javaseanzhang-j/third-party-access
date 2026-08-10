package com.ftk.tpip.runtime;

import com.ftk.tpip.routing.domain.model.RouteTargetHealth;

/** Runtime-owned health facts; implementations must not read design-time configuration tables. */
public interface RuntimeRouteHealthRegistry {
    RouteTargetHealth health(long bindingId);
    void record(long bindingId, boolean successful);

    static RuntimeRouteHealthRegistry optimistic() {
        return new RuntimeRouteHealthRegistry() {
            @Override public RouteTargetHealth health(long bindingId) { return RouteTargetHealth.HEALTHY; }
            @Override public void record(long bindingId, boolean successful) { }
        };
    }
}
