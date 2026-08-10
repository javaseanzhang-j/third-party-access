package com.ftk.tpip.runtime;

import java.time.Instant;

public record DeploymentRouteResolverSnapshot(String operationCode, String environmentCode,
        String revision, BundleCacheState state, Instant loadedAt, Instant lastAttemptAt,
        Instant lastSuccessAt, String lastError, long fallbackCount, int targetCount) {}
