package com.ftk.tpip.worker.health;

import java.math.BigDecimal;
import java.time.Instant;

public record HealthCandidate(long deploymentId, String deploymentCode, long operationId,
        String environmentCode, BigDecimal trafficPercentage, Instant activatedAt) {}
