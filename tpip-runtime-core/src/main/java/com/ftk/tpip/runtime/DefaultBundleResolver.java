package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.bundle.BundleIntegrity;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.mapping.api.MappingDirection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class DefaultBundleResolver implements BundleResolver {
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final BundleReferenceRegistry references;
    private final BundleArtifactSource artifacts;
    private final BundleManifestDecoder decoder;
    private final ObjectMapper json;
    private final RuntimeVersion runtimeVersion;
    private final Duration cacheTtl;
    private final Duration maxStale;
    private final int maxArtifactBytes;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Map<CacheKey, Object> locks = new ConcurrentHashMap<>();

    public DefaultBundleResolver(
            BundleReferenceRegistry references,
            BundleArtifactSource artifacts,
            BundleManifestDecoder decoder,
            ObjectMapper json,
            RuntimeVersion runtimeVersion,
            Duration cacheTtl,
            Duration maxStale,
            int maxArtifactBytes,
            Clock clock) {
        this.references = Objects.requireNonNull(references);
        this.artifacts = Objects.requireNonNull(artifacts);
        this.decoder = Objects.requireNonNull(decoder);
        this.json = Objects.requireNonNull(json);
        this.runtimeVersion = Objects.requireNonNull(runtimeVersion);
        this.cacheTtl = positiveOrZero(cacheTtl, "cacheTtl");
        this.maxStale = positiveOrZero(maxStale, "maxStale");
        if (maxStale.compareTo(cacheTtl) < 0) throw new IllegalArgumentException("maxStale must be >= cacheTtl");
        if (maxArtifactBytes < 1024) throw new IllegalArgumentException("maxArtifactBytes must be >= 1024");
        this.maxArtifactBytes = maxArtifactBytes;
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public DeploymentBundleManifest resolve(String operationCode, String environmentCode) {
        OperationKey operation = new OperationKey(operationCode, environmentCode);
        BundleCoordinate coordinate = references.activeBundle(operation.operationCode, operation.environmentCode)
                .orElseThrow(() -> new BundleResolutionException(BundleResolutionCode.REFERENCE_NOT_FOUND,
                        "No active Bundle reference for " + operation.identity()));
        return resolve(operation.operationCode, operation.environmentCode, coordinate);
    }

    public DeploymentBundleManifest resolve(String operationCode, String environmentCode,
            BundleCoordinate coordinate) {
        CacheKey key = new CacheKey(operationCode, environmentCode, coordinate);
        Instant now = clock.instant();
        CacheEntry current = cache.get(key);
        if (isFresh(current, coordinate, now)) return current.manifest;

        synchronized (locks.computeIfAbsent(key, ignored -> new Object())) {
            now = clock.instant();
            current = cache.get(key);
            if (isFresh(current, coordinate, now)) return current.manifest;
            try {
                CacheEntry loaded = load(key, coordinate, now);
                cache.put(key, loaded);
                return loaded.manifest;
            } catch (RuntimeException failure) {
                if (current != null && !now.isAfter(current.loadedAt.plus(maxStale))) {
                    cache.put(key, current.fallback(now, safeMessage(failure)));
                    return current.manifest;
                }
                if (current != null) {
                    throw new BundleResolutionException(BundleResolutionCode.LAST_KNOWN_GOOD_EXPIRED,
                            "Last Known Good Bundle expired for " + key.identity(), failure);
                }
                if (failure instanceof BundleResolutionException resolutionException) throw resolutionException;
                throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                        "Bundle load failed for " + key.identity(), failure);
            }
        }
    }

    public List<BundleResolverSnapshot> snapshots() {
        List<BundleResolverSnapshot> result = new ArrayList<>();
        cache.forEach((key, entry) -> result.add(new BundleResolverSnapshot(
                key.operationCode, key.environmentCode, entry.coordinate.bundleCode(),
                entry.coordinate.bundleVersion(), entry.artifactChecksum, entry.state,
                entry.loadedAt, entry.lastAttemptAt, entry.lastSuccessAt, entry.lastError, entry.fallbackCount)));
        return result.stream().sorted(Comparator.comparing(BundleResolverSnapshot::operationCode)
                .thenComparing(BundleResolverSnapshot::environmentCode)).toList();
    }

    public void invalidate(String operationCode, String environmentCode) {
        OperationKey operation = new OperationKey(operationCode, environmentCode);
        cache.keySet().removeIf(key -> key.operationCode.equals(operation.operationCode)
                && key.environmentCode.equals(operation.environmentCode));
    }

    private CacheEntry load(CacheKey key, BundleCoordinate coordinate, Instant now) {
        BundleArtifact artifact;
        try {
            artifact = artifacts.fetch(coordinate);
        } catch (BundleResolutionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE,
                    "Bundle source failed for " + coordinate.identity(), exception);
        }
        byte[] content = artifact.content();
        if (content.length > maxArtifactBytes) {
            throw new BundleResolutionException(BundleResolutionCode.ARTIFACT_TOO_LARGE,
                    "Bundle exceeds maxArtifactBytes: " + content.length);
        }
        String declared = artifact.artifactChecksum();
        if (declared == null || !SHA256.matcher(declared).matches()) {
            throw new BundleResolutionException(BundleResolutionCode.ARTIFACT_CHECKSUM_MISSING,
                    "Bundle source did not provide a valid SHA-256 checksum");
        }
        String actual = BundleIntegrity.artifactChecksum(content);
        if (!same(actual, declared)) {
            throw new BundleResolutionException(BundleResolutionCode.ARTIFACT_CHECKSUM_MISMATCH,
                    "Downloaded Bundle checksum does not match response metadata");
        }
        if (coordinate.expectedArtifactChecksum() != null && !same(actual, coordinate.expectedArtifactChecksum())) {
            throw new BundleResolutionException(BundleResolutionCode.ARTIFACT_CHECKSUM_MISMATCH,
                    "Downloaded Bundle checksum does not match activated reference");
        }

        DeploymentBundleManifest manifest;
        try {
            manifest = decoder.decode(content);
        } catch (RuntimeException exception) {
            throw new BundleResolutionException(BundleResolutionCode.MANIFEST_INVALID,
                    "Bundle Manifest cannot be decoded", exception);
        }
        validateManifest(key, coordinate, manifest);
        return new CacheEntry(manifest, coordinate, actual, BundleCacheState.FRESH,
                now, now, now, null, 0);
    }

    private void validateManifest(CacheKey key, BundleCoordinate coordinate, DeploymentBundleManifest manifest) {
        if (!coordinate.bundleCode().equals(manifest.bundleCode())
                || !coordinate.bundleVersion().equals(manifest.bundleVersion())
                || !key.operationCode.equals(manifest.operationCode())
                || !key.environmentCode.equals(manifest.environmentCode())) {
            throw new BundleResolutionException(BundleResolutionCode.MANIFEST_IDENTITY_MISMATCH,
                    "Bundle Manifest identity does not match activated reference");
        }
        if (manifest.canonicalRequestSchema() == null || manifest.canonicalResponseSchema() == null
                || manifest.providerContractSnapshot() == null || manifest.endpointSnapshot() == null
                || manifest.bindingVersion() == null || manifest.bindingVersion().isBlank()) {
            throw new BundleResolutionException(BundleResolutionCode.MANIFEST_INVALID,
                    "Bundle Manifest is missing a required runtime snapshot");
        }
        EnumSet<MappingDirection> directions = EnumSet.noneOf(MappingDirection.class);
        manifest.mappingPlans().forEach(plan -> {
            if (!directions.add(plan.direction())) {
                throw new BundleResolutionException(BundleResolutionCode.MANIFEST_INVALID,
                        "Bundle Manifest contains duplicate mapping direction: " + plan.direction());
            }
        });
        if (!directions.contains(MappingDirection.OUTBOUND_REQUEST)
                || !directions.contains(MappingDirection.INBOUND_RESPONSE)) {
            throw new BundleResolutionException(BundleResolutionCode.MANIFEST_INVALID,
                    "Bundle Manifest requires request and response mapping plans");
        }
        if (!BundleIntegrity.contentChecksumMatches(json, manifest)) {
            throw new BundleResolutionException(BundleResolutionCode.CONTENT_CHECKSUM_MISMATCH,
                    "Bundle Manifest content checksum is invalid");
        }
        try {
            if (!runtimeVersion.satisfies(manifest.runtimeCompatibility())) {
                throw new BundleResolutionException(BundleResolutionCode.RUNTIME_INCOMPATIBLE,
                        "Bundle requires runtime " + manifest.runtimeCompatibility() + ", current is " + runtimeVersion);
            }
        } catch (IllegalArgumentException exception) {
            throw new BundleResolutionException(BundleResolutionCode.RUNTIME_INCOMPATIBLE,
                    "Bundle runtimeCompatibility is invalid", exception);
        }
    }

    private boolean isFresh(CacheEntry entry, BundleCoordinate coordinate, Instant now) {
        return entry != null && entry.coordinate.equals(coordinate)
                && !now.isAfter(entry.loadedAt.plus(cacheTtl));
    }

    private static boolean same(String first, String second) {
        return MessageDigest.isEqual(first.getBytes(StandardCharsets.US_ASCII), second.getBytes(StandardCharsets.US_ASCII));
    }

    private static Duration positiveOrZero(Duration value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isNegative()) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static String safeMessage(RuntimeException exception) {
        String value = exception instanceof BundleResolutionException resolution
                ? resolution.code() + ": " + resolution.getMessage() : exception.getClass().getSimpleName();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private record CacheKey(String operationCode, String environmentCode, BundleCoordinate coordinate) {
        private CacheKey {
            operationCode = required(operationCode, "operationCode");
            environmentCode = required(environmentCode, "environmentCode");
            Objects.requireNonNull(coordinate, "coordinate must not be null");
        }
        private String identity() { return operationCode + "@" + environmentCode; }
        private static String required(String value, String name) {
            String normalized = Objects.requireNonNull(value, name + " must not be null").trim();
            if (normalized.isEmpty()) throw new IllegalArgumentException(name + " must not be blank");
            return normalized;
        }
    }

    private record OperationKey(String operationCode, String environmentCode) {
        private OperationKey {
            operationCode = CacheKey.required(operationCode, "operationCode");
            environmentCode = CacheKey.required(environmentCode, "environmentCode");
        }
        private String identity() { return operationCode + "@" + environmentCode; }
    }

    private record CacheEntry(
            DeploymentBundleManifest manifest,
            BundleCoordinate coordinate,
            String artifactChecksum,
            BundleCacheState state,
            Instant loadedAt,
            Instant lastAttemptAt,
            Instant lastSuccessAt,
            String lastError,
            long fallbackCount) {
        private CacheEntry fallback(Instant now, String error) {
            return new CacheEntry(manifest, coordinate, artifactChecksum, BundleCacheState.STALE_FALLBACK,
                    loadedAt, now, lastSuccessAt, error, fallbackCount + 1);
        }
    }
}
