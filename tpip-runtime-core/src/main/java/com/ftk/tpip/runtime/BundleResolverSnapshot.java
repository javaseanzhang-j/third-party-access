package com.ftk.tpip.runtime;

import java.time.Instant;

public record BundleResolverSnapshot(
        String operationCode,
        String environmentCode,
        String bundleCode,
        String bundleVersion,
        String artifactChecksum,
        BundleCacheState state,
        Instant loadedAt,
        Instant lastAttemptAt,
        Instant lastSuccessAt,
        String lastError,
        long fallbackCount) {}
