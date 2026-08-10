package com.ftk.tpip.contract;

public record ResponseMetadata(
        String requestId,
        String traceId,
        String operationCode,
        String bundleVersion,
        String providerCode,
        long durationMs) {
}
