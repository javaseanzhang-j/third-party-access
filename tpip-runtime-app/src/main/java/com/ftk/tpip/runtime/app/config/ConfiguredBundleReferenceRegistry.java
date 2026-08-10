package com.ftk.tpip.runtime.app.config;

import com.ftk.tpip.runtime.BundleCoordinate;
import com.ftk.tpip.runtime.BundleReferenceRegistry;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ConfiguredBundleReferenceRegistry implements BundleReferenceRegistry {
    private final Map<Key, BundleCoordinate> routes;

    public ConfiguredBundleReferenceRegistry(List<RuntimeBundleProperties.Route> configured) {
        Map<Key, BundleCoordinate> result = new LinkedHashMap<>();
        for (RuntimeBundleProperties.Route route : configured) {
            Key key = new Key(required(route.getOperationCode(), "operationCode"),
                    required(route.getEnvironmentCode(), "environmentCode"));
            BundleCoordinate coordinate = new BundleCoordinate(
                    route.getBundleCode(), route.getBundleVersion(), blankToNull(route.getArtifactChecksum()));
            if (result.putIfAbsent(key, coordinate) != null) {
                throw new IllegalArgumentException("Duplicate runtime Bundle route: " + key.operationCode + "@" + key.environmentCode);
            }
        }
        routes = Map.copyOf(result);
    }

    @Override
    public Optional<BundleCoordinate> activeBundle(String operationCode, String environmentCode) {
        return Optional.ofNullable(routes.get(new Key(required(operationCode, "operationCode"),
                required(environmentCode, "environmentCode"))));
    }

    public List<ConfiguredRoute> routes() {
        return routes.entrySet().stream().map(entry -> new ConfiguredRoute(
                entry.getKey().operationCode, entry.getKey().environmentCode,
                entry.getValue().bundleCode(), entry.getValue().bundleVersion(),
                entry.getValue().expectedArtifactChecksum() != null)).toList();
    }

    public record ConfiguredRoute(String operationCode, String environmentCode, String bundleCode,
                                  String bundleVersion, boolean checksumPinned) {}
    private record Key(String operationCode, String environmentCode) {}

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
