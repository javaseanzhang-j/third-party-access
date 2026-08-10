package com.ftk.tpip.runtime;

import com.ftk.tpip.bundle.DeploymentBundleManifest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultDeploymentResolver implements DeploymentResolver {
    private final DeploymentRouteSource routes;
    private final DefaultBundleResolver bundles;
    private final Duration routeTtl;
    private final Duration maxStale;
    private final Clock clock;
    private final Map<RouteKey, RouteEntry> cache = new ConcurrentHashMap<>();
    private final Map<RouteKey, Object> locks = new ConcurrentHashMap<>();

    public DefaultDeploymentResolver(DeploymentRouteSource routes, DefaultBundleResolver bundles,
            Duration routeTtl, Duration maxStale, Clock clock) {
        this.routes = Objects.requireNonNull(routes);
        this.bundles = Objects.requireNonNull(bundles);
        this.routeTtl = duration(routeTtl, "routeTtl");
        this.maxStale = duration(maxStale, "maxStale");
        if (maxStale.compareTo(routeTtl) < 0) throw new IllegalArgumentException("maxStale must be >= routeTtl");
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public ResolvedDeployment resolve(String operationCode, String environmentCode, String routingKey) {
        RouteKey key = new RouteKey(operationCode, environmentCode);
        RouteEntry entry = route(key);
        WeightedDeploymentTarget target = select(entry.route.targets(), required(routingKey, "routingKey"));
        DeploymentBundleManifest bundle = bundles.resolve(key.operationCode, key.environmentCode, target.bundle());
        return new ResolvedDeployment(target.deploymentId(), target.deploymentCode(), entry.route.revision(),
                target.trafficPercentage(), bundle);
    }

    public List<DeploymentRouteResolverSnapshot> snapshots() {
        List<DeploymentRouteResolverSnapshot> snapshots = new ArrayList<>();
        cache.forEach((key, entry) -> snapshots.add(new DeploymentRouteResolverSnapshot(
                key.operationCode, key.environmentCode, entry.route.revision(), entry.state,
                entry.loadedAt, entry.lastAttemptAt, entry.lastSuccessAt, entry.lastError,
                entry.fallbackCount, entry.route.targets().size())));
        return snapshots.stream().sorted(Comparator.comparing(DeploymentRouteResolverSnapshot::operationCode)
                .thenComparing(DeploymentRouteResolverSnapshot::environmentCode)).toList();
    }

    private RouteEntry route(RouteKey key) {
        Instant now = clock.instant();
        RouteEntry current = cache.get(key);
        if (current != null && !now.isAfter(current.loadedAt.plus(routeTtl))) return current;
        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            now = clock.instant();
            current = cache.get(key);
            if (current != null && !now.isAfter(current.loadedAt.plus(routeTtl))) return current;
            try {
                DeploymentRouteSnapshot loaded = routes.fetch(key.operationCode, key.environmentCode);
                if (!key.operationCode.equals(loaded.operationCode())
                        || !key.environmentCode.equals(loaded.environmentCode())) {
                    throw new BundleResolutionException(BundleResolutionCode.MANIFEST_IDENTITY_MISMATCH,
                            "Deployment route identity mismatch");
                }
                RouteEntry success = new RouteEntry(loaded, BundleCacheState.FRESH,
                        now, now, now, null, 0);
                cache.put(key, success);
                return success;
            } catch (RuntimeException failure) {
                if (current != null && !now.isAfter(current.loadedAt.plus(maxStale))) {
                    RouteEntry fallback = current.fallback(now, safe(failure));
                    cache.put(key, fallback);
                    return fallback;
                }
                if (failure instanceof BundleResolutionException resolution) throw resolution;
                throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                        "Deployment route unavailable for " + key.operationCode + "@" + key.environmentCode, failure);
            }
        }
    }

    static WeightedDeploymentTarget select(List<WeightedDeploymentTarget> targets, String routingKey) {
        List<WeightedDeploymentTarget> ordered = targets.stream()
                .sorted(Comparator.comparingLong(WeightedDeploymentTarget::deploymentId)).toList();
        int bucket = bucket(routingKey);
        int boundary = 0;
        for (WeightedDeploymentTarget target : ordered) {
            boundary += target.trafficPercentage().multiply(new BigDecimal("100")).intValueExact();
            if (bucket < boundary) return target;
        }
        return ordered.getLast();
    }

    private static int bucket(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            long value = 0;
            for (int index = 0; index < 8; index++) value = (value << 8) | (digest[index] & 0xffL);
            return (int) Long.remainderUnsigned(value, 10_000);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static Duration duration(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }
    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    private static String safe(RuntimeException failure) {
        String value = failure instanceof BundleResolutionException resolution
                ? resolution.code() + ": " + resolution.getMessage() : failure.getClass().getSimpleName();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private record RouteKey(String operationCode, String environmentCode) {
        private RouteKey {
            operationCode = required(operationCode, "operationCode");
            environmentCode = required(environmentCode, "environmentCode");
        }
    }
    private record RouteEntry(DeploymentRouteSnapshot route, BundleCacheState state, Instant loadedAt,
            Instant lastAttemptAt, Instant lastSuccessAt, String lastError, long fallbackCount) {
        private RouteEntry fallback(Instant now, String error) {
            return new RouteEntry(route, BundleCacheState.STALE_FALLBACK, loadedAt, now,
                    lastSuccessAt, error, fallbackCount + 1);
        }
    }
}
