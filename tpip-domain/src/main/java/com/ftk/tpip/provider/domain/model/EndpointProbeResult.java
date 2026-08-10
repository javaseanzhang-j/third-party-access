package com.ftk.tpip.provider.domain.model;

import java.time.Instant;

public record EndpointProbeResult(Long id, long endpointId, String outcome, String reasonCode,
        long latencyMs, String actorCode, Instant createdAt) {}
