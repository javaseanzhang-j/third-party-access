package com.ftk.tpip.provider.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

public record ProviderEndpoint(
        Long id,
        long providerContractId,
        AssetCode endpointCode,
        String environmentCode,
        int revisionNo,
        EndpointScheme protocolScheme,
        String baseUrl,
        String resourcePath,
        EndpointHttpMethod httpMethod,
        String contentType,
        String charsetName,
        int connectTimeoutMs,
        int readTimeoutMs,
        int totalTimeoutMs,
        Long credentialRefId,
        String networkConfig,
        String tlsConfig,
        EndpointLifecycleStatus lifecycleStatus,
        String contentChecksum,
        Instant publishedAt,
        Instant createdAt) {

    private static final Pattern ENVIRONMENT = Pattern.compile("^[a-z][a-z0-9_-]*$");
    private static final Pattern SHA_256 = Pattern.compile("^[a-f0-9]{64}$");

    public ProviderEndpoint {
        if (id != null && id <= 0) {
            throw new IllegalArgumentException("id must be positive");
        }
        if (providerContractId <= 0) {
            throw new IllegalArgumentException("providerContractId must be positive");
        }
        endpointCode = Objects.requireNonNull(endpointCode, "endpointCode must not be null");
        environmentCode = requiredText(environmentCode, "environmentCode", 32);
        if (!ENVIRONMENT.matcher(environmentCode).matches()) {
            throw new IllegalArgumentException("Invalid environmentCode: " + environmentCode);
        }
        if (revisionNo < 0) {
            throw new IllegalArgumentException("revisionNo must not be negative");
        }
        protocolScheme = Objects.requireNonNull(protocolScheme, "protocolScheme must not be null");
        baseUrl = validateBaseUrl(baseUrl, protocolScheme);
        resourcePath = validateResourcePath(resourcePath);
        httpMethod = Objects.requireNonNull(httpMethod, "httpMethod must not be null");
        contentType = optionalText(contentType, "contentType", 100);
        charsetName = requiredText(charsetName, "charsetName", 32);
        if (!Charset.isSupported(charsetName)) {
            throw new IllegalArgumentException("Unsupported charsetName: " + charsetName);
        }
        if (connectTimeoutMs < 1 || readTimeoutMs < 1 || totalTimeoutMs < 1) {
            throw new IllegalArgumentException("timeouts must be positive");
        }
        if (totalTimeoutMs < connectTimeoutMs || totalTimeoutMs < readTimeoutMs) {
            throw new IllegalArgumentException("totalTimeoutMs must cover connectTimeoutMs and readTimeoutMs");
        }
        if (credentialRefId != null && credentialRefId <= 0) {
            throw new IllegalArgumentException("credentialRefId must be positive");
        }
        contentChecksum = Objects.requireNonNull(contentChecksum, "contentChecksum must not be null");
        if (!SHA_256.matcher(contentChecksum).matches()) {
            throw new IllegalArgumentException("contentChecksum must be a lowercase SHA-256 value");
        }
        lifecycleStatus = Objects.requireNonNull(lifecycleStatus, "lifecycleStatus must not be null");
        if (lifecycleStatus == EndpointLifecycleStatus.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException("published endpoint must have publishedAt");
        }
    }

    public static ProviderEndpoint draft(
            long providerContractId,
            AssetCode endpointCode,
            String environmentCode,
            EndpointScheme protocolScheme,
            String baseUrl,
            String resourcePath,
            EndpointHttpMethod httpMethod,
            String contentType,
            String charsetName,
            int connectTimeoutMs,
            int readTimeoutMs,
            int totalTimeoutMs,
            Long credentialRefId,
            String networkConfig,
            String tlsConfig,
            String contentChecksum) {
        return new ProviderEndpoint(
                null,
                providerContractId,
                endpointCode,
                environmentCode,
                0,
                protocolScheme,
                baseUrl,
                resourcePath,
                httpMethod,
                contentType,
                charsetName,
                connectTimeoutMs,
                readTimeoutMs,
                totalTimeoutMs,
                credentialRefId,
                networkConfig,
                tlsConfig,
                EndpointLifecycleStatus.DRAFT,
                contentChecksum,
                null,
                null);
    }

    private static String validateBaseUrl(String value, EndpointScheme scheme) {
        String normalized = requiredText(value, "baseUrl", 500);
        try {
            URI uri = new URI(normalized);
            if (!scheme.value().equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("baseUrl scheme must match protocolScheme");
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("baseUrl must contain a host");
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("baseUrl must not contain user info, query or fragment");
            }
            return normalized.endsWith("/")
                    ? normalized.substring(0, normalized.length() - 1)
                    : normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid baseUrl: " + value, exception);
        }
    }

    private static String validateResourcePath(String value) {
        String normalized = requiredText(value, "resourcePath", 500);
        if (!normalized.startsWith("/")) {
            throw new IllegalArgumentException("resourcePath must start with '/'");
        }
        if (normalized.contains("?") || normalized.contains("#")) {
            throw new IllegalArgumentException("resourcePath must not contain query or fragment");
        }
        return normalized;
    }

    private static String requiredText(String value, String field, int maxLength) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1 to " + maxLength + " characters");
        }
        return normalized;
    }

    private static String optionalText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
        return normalized;
    }
}
