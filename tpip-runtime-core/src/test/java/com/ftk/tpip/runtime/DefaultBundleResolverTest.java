package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.bundle.BundleIntegrity;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.mapping.api.MappingDirection;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultBundleResolverTest {
    private final ObjectMapper json = new ObjectMapper();
    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-08T00:00:00Z"));
    private final BundleCoordinate coordinate = new BundleCoordinate("order.provider.test", "1.0.0", null);

    @Test
    void loadsOnceAndServesFreshL1Cache() {
        DeploymentBundleManifest manifest = manifest("order.create", "test", coordinate, ">=0.1 <1.0");
        byte[] bytes = "artifact-v1".getBytes();
        AtomicInteger fetches = new AtomicInteger();
        DefaultBundleResolver resolver = resolver((ignored) -> {
            fetches.incrementAndGet();
            return artifact(bytes);
        }, ignored -> manifest, new AtomicReference<>(coordinate), Duration.ofMinutes(5), Duration.ofHours(1));

        assertSame(manifest, resolver.resolve("order.create", "test"));
        assertSame(manifest, resolver.resolve("order.create", "test"));

        assertEquals(1, fetches.get());
        assertEquals(BundleCacheState.FRESH, resolver.snapshots().getFirst().state());
    }

    @Test
    void coalescesConcurrentLoadsForTheSameOperationAndEnvironment() throws Exception {
        DeploymentBundleManifest manifest = manifest("order.create", "test", coordinate, ">=0.1 <1.0");
        AtomicInteger fetches = new AtomicInteger();
        DefaultBundleResolver resolver = resolver(ignored -> {
            fetches.incrementAndGet();
            return artifact("artifact-v1".getBytes());
        }, ignored -> manifest, new AtomicReference<>(coordinate), Duration.ofMinutes(5), Duration.ofHours(1));
        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(callers);
        try {
            List<Future<DeploymentBundleManifest>> futures = java.util.stream.IntStream.range(0, callers)
                    .mapToObj(ignored -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return resolver.resolve("order.create", "test");
                    })).toList();
            ready.await();
            start.countDown();
            for (Future<DeploymentBundleManifest> future : futures) assertSame(manifest, future.get());
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, fetches.get());
    }

    @Test
    void cachesTwoCanaryBundleCoordinatesIndependently() {
        BundleCoordinate secondCoordinate = new BundleCoordinate("order.provider.test.v2", "2.0.0", null);
        DeploymentBundleManifest first = manifest("order.create", "test", coordinate, ">=0.1 <1.0");
        DeploymentBundleManifest second = manifest("order.create", "test", secondCoordinate, ">=0.1 <1.0");
        AtomicInteger fetches = new AtomicInteger();
        AtomicReference<DeploymentBundleManifest> decoded = new AtomicReference<>(first);
        DefaultBundleResolver resolver = resolver(ignored -> {
            fetches.incrementAndGet();
            return artifact(ignored.identity().getBytes());
        }, ignored -> decoded.get(), new AtomicReference<>(coordinate), Duration.ofMinutes(5), Duration.ofHours(1));

        resolver.resolve("order.create", "test", coordinate);
        decoded.set(second);
        resolver.resolve("order.create", "test", secondCoordinate);

        assertEquals(2, fetches.get());
        assertEquals(2, resolver.snapshots().size());
    }

    @Test
    void fallsBackToLastKnownGoodWithinBoundedStaleWindow() {
        DeploymentBundleManifest manifest = manifest("order.create", "test", coordinate, ">=0.1 <1.0");
        byte[] bytes = "artifact-v1".getBytes();
        AtomicBoolean fail = new AtomicBoolean();
        DefaultBundleResolver resolver = resolver(ignored -> {
            if (fail.get()) throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE, "offline");
            return artifact(bytes);
        }, ignored -> manifest, new AtomicReference<>(coordinate), Duration.ofMinutes(1), Duration.ofMinutes(10));
        resolver.resolve("order.create", "test");

        clock.advance(Duration.ofMinutes(2));
        fail.set(true);

        assertSame(manifest, resolver.resolve("order.create", "test"));
        BundleResolverSnapshot snapshot = resolver.snapshots().getFirst();
        assertEquals(BundleCacheState.STALE_FALLBACK, snapshot.state());
        assertEquals(1, snapshot.fallbackCount());
    }

    @Test
    void rejectsFallbackAfterLastKnownGoodExpires() {
        DeploymentBundleManifest manifest = manifest("order.create", "test", coordinate, ">=0.1 <1.0");
        AtomicBoolean fail = new AtomicBoolean();
        DefaultBundleResolver resolver = resolver(ignored -> {
            if (fail.get()) throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE, "offline");
            return artifact("artifact-v1".getBytes());
        }, ignored -> manifest, new AtomicReference<>(coordinate), Duration.ofMinutes(1), Duration.ofMinutes(10));
        resolver.resolve("order.create", "test");

        clock.advance(Duration.ofMinutes(11));
        fail.set(true);
        BundleResolutionException exception = assertThrows(BundleResolutionException.class,
                () -> resolver.resolve("order.create", "test"));

        assertEquals(BundleResolutionCode.LAST_KNOWN_GOOD_EXPIRED, exception.code());
    }

    @Test
    void rejectsArtifactChecksumMismatchBeforeDecoding() {
        AtomicInteger decodes = new AtomicInteger();
        DefaultBundleResolver resolver = resolver(ignored -> new BundleArtifact(
                        "artifact-v1".getBytes(), "0".repeat(64), null),
                ignored -> { decodes.incrementAndGet(); return manifest("order.create", "test", coordinate, ">=0.1 <1.0"); },
                new AtomicReference<>(coordinate), Duration.ofMinutes(1), Duration.ofMinutes(10));

        BundleResolutionException exception = assertThrows(BundleResolutionException.class,
                () -> resolver.resolve("order.create", "test"));

        assertEquals(BundleResolutionCode.ARTIFACT_CHECKSUM_MISMATCH, exception.code());
        assertEquals(0, decodes.get());
    }

    @Test
    void rejectsManifestIdentityAndRuntimeCompatibility() {
        byte[] bytes = "artifact-v1".getBytes();
        DeploymentBundleManifest wrongIdentity = manifest("order.cancel", "test", coordinate, ">=0.1 <1.0");
        DefaultBundleResolver identityResolver = resolver(ignored -> artifact(bytes), ignored -> wrongIdentity,
                new AtomicReference<>(coordinate), Duration.ofMinutes(1), Duration.ofMinutes(10));
        assertEquals(BundleResolutionCode.MANIFEST_IDENTITY_MISMATCH,
                assertThrows(BundleResolutionException.class,
                        () -> identityResolver.resolve("order.create", "test")).code());

        DeploymentBundleManifest incompatible = manifest("order.create", "test", coordinate, ">=2.0 <3.0");
        DefaultBundleResolver versionResolver = resolver(ignored -> artifact(bytes), ignored -> incompatible,
                new AtomicReference<>(coordinate), Duration.ofMinutes(1), Duration.ofMinutes(10));
        assertEquals(BundleResolutionCode.RUNTIME_INCOMPATIBLE,
                assertThrows(BundleResolutionException.class,
                        () -> versionResolver.resolve("order.create", "test")).code());
    }

    private DefaultBundleResolver resolver(BundleArtifactSource source, BundleManifestDecoder decoder,
            AtomicReference<BundleCoordinate> active, Duration ttl, Duration maxStale) {
        BundleReferenceRegistry registry = (operation, environment) -> Optional.ofNullable(active.get());
        return new DefaultBundleResolver(registry, source, decoder, json, RuntimeVersion.parse("0.1.0"),
                ttl, maxStale, 1024, clock);
    }

    private BundleArtifact artifact(byte[] bytes) {
        return new BundleArtifact(bytes, BundleIntegrity.artifactChecksum(bytes), null);
    }

    private DeploymentBundleManifest manifest(String operation, String environment,
            BundleCoordinate bundle, String compatibility) {
        List<CompiledMappingPlan> mappings = List.of(
                new CompiledMappingPlan("response", 1, MappingDirection.INBOUND_RESPONSE, List.of(), "b".repeat(64)),
                new CompiledMappingPlan("request", 1, MappingDirection.OUTBOUND_REQUEST, List.of(), "a".repeat(64)));
        var schema = json.createObjectNode().put("type", "object");
        var provisional = new DeploymentBundleManifest(bundle.bundleCode(), bundle.bundleVersion(), operation,
                environment, "order.provider@1", schema, schema, json.createObjectNode(), mappings, null,
                json.createObjectNode(), List.of(), compatibility, "", clock.instant());
        return new DeploymentBundleManifest(bundle.bundleCode(), bundle.bundleVersion(), operation,
                environment, provisional.bindingVersion(), schema, schema, json.createObjectNode(), mappings, null,
                json.createObjectNode(), List.of(), compatibility,
                BundleIntegrity.contentChecksum(json, provisional), clock.instant());
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        private void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
