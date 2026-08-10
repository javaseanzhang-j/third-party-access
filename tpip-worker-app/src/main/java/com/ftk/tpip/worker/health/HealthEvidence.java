package com.ftk.tpip.worker.health;

import com.fasterxml.jackson.databind.JsonNode;

public record HealthEvidence(long sampleCount, long failureCount, long p95LatencyMs, JsonNode evidence) {}
