package com.ftk.tpip.runtime;

import com.ftk.tpip.routing.domain.model.RouteTargetHealth;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/** Passive per-instance health registry. New targets start healthy and are ejected after consecutive failures. */
public final class InMemoryRuntimeRouteHealthRegistry implements RuntimeRouteHealthRegistry {
    private final ConcurrentHashMap<Long, AtomicInteger> failures = new ConcurrentHashMap<>();
    private final int failureThreshold;

    public InMemoryRuntimeRouteHealthRegistry(int failureThreshold) {
        if (failureThreshold < 1) throw new IllegalArgumentException("failureThreshold must be positive");
        this.failureThreshold = failureThreshold;
    }

    @Override public RouteTargetHealth health(long bindingId) {
        AtomicInteger count = failures.get(bindingId);
        return count != null && count.get() >= failureThreshold
                ? RouteTargetHealth.UNHEALTHY : RouteTargetHealth.HEALTHY;
    }

    @Override public void record(long bindingId, boolean successful) {
        if (successful) failures.remove(bindingId);
        else failures.computeIfAbsent(bindingId, ignored -> new AtomicInteger()).incrementAndGet();
    }
}
