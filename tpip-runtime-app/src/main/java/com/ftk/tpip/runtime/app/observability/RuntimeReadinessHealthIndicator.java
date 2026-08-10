package com.ftk.tpip.runtime.app.observability;

import com.ftk.tpip.runtime.BundleCacheState;
import com.ftk.tpip.runtime.DefaultBundleResolver;
import com.ftk.tpip.runtime.DefaultDeploymentResolver;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("runtimeReadiness")
public final class RuntimeReadinessHealthIndicator implements HealthIndicator {
    private final DefaultBundleResolver bundles;
    private final DefaultDeploymentResolver routes;

    public RuntimeReadinessHealthIndicator(DefaultBundleResolver bundles, DefaultDeploymentResolver routes) {
        this.bundles = bundles;
        this.routes = routes;
    }

    @Override
    public Health health() {
        var bundleSnapshots = bundles.snapshots();
        var routeSnapshots = routes.snapshots();
        long staleBundles = bundleSnapshots.stream().filter(value -> value.state() == BundleCacheState.STALE_FALLBACK).count();
        long staleRoutes = routeSnapshots.stream().filter(value -> value.state() == BundleCacheState.STALE_FALLBACK).count();
        Health.Builder builder = staleBundles + staleRoutes == 0 ? Health.up() : Health.status("DEGRADED");
        return builder.withDetail("state", bundleSnapshots.isEmpty() ? "COLD" : "READY")
                .withDetail("loadedBundles", bundleSnapshots.size())
                .withDetail("deploymentRoutes", routeSnapshots.size())
                .withDetail("staleBundles", staleBundles)
                .withDetail("staleRoutes", staleRoutes)
                .build();
    }
}
