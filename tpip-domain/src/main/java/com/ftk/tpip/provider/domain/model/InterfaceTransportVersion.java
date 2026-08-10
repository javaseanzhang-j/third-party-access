package com.ftk.tpip.provider.domain.model;

import com.ftk.tpip.shared.SemanticVersion;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Versioned HTTP details of one third-party interface. Base URL belongs to the selected access channel. */
public record InterfaceTransportVersion(Long id, long providerContractId, int versionNo,
        SemanticVersion semanticVersion, String resourcePath, EndpointHttpMethod httpMethod,
        String contentType, String charsetName, Integer connectTimeoutMs, Integer readTimeoutMs,
        Integer totalTimeoutMs, String transportMetadata, String contentChecksum,
        EndpointLifecycleStatus lifecycleStatus, Instant publishedAt, Instant createdAt) {
    private static final Pattern SHA = Pattern.compile("^[a-f0-9]{64}$");
    public InterfaceTransportVersion {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (providerContractId <= 0) throw new IllegalArgumentException("providerContractId must be positive");
        if (versionNo < 0) throw new IllegalArgumentException("versionNo must not be negative");
        semanticVersion = Objects.requireNonNull(semanticVersion, "semanticVersion must not be null");
        resourcePath = path(resourcePath);
        httpMethod = Objects.requireNonNull(httpMethod, "httpMethod must not be null");
        contentType = optional(contentType, 100);
        charsetName = required(charsetName, "charsetName", 32);
        if (!Charset.isSupported(charsetName)) throw new IllegalArgumentException("unsupported charsetName");
        positive(connectTimeoutMs, "connectTimeoutMs"); positive(readTimeoutMs, "readTimeoutMs");
        positive(totalTimeoutMs, "totalTimeoutMs");
        if (totalTimeoutMs != null && connectTimeoutMs != null && totalTimeoutMs < connectTimeoutMs)
            throw new IllegalArgumentException("totalTimeoutMs must cover connectTimeoutMs");
        if (totalTimeoutMs != null && readTimeoutMs != null && totalTimeoutMs < readTimeoutMs)
            throw new IllegalArgumentException("totalTimeoutMs must cover readTimeoutMs");
        transportMetadata = optional(transportMetadata, Integer.MAX_VALUE);
        if (contentChecksum == null || !SHA.matcher(contentChecksum).matches())
            throw new IllegalArgumentException("invalid contentChecksum");
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == EndpointLifecycleStatus.PUBLISHED && publishedAt == null)
            throw new IllegalArgumentException("published transport version requires publishedAt");
    }
    private static String path(String value) {
        String normalized = required(value, "resourcePath", 500);
        if (!normalized.startsWith("/") || normalized.contains("?") || normalized.contains("#"))
            throw new IllegalArgumentException("resourcePath must be an absolute path without query or fragment");
        return normalized;
    }
    private static void positive(Integer value, String field) {
        if (value != null && value <= 0) throw new IllegalArgumentException(field + " must be positive");
    }
    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("value is too long");
        return normalized;
    }
}
