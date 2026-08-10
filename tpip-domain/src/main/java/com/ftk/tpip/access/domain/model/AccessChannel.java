package com.ftk.tpip.access.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Objects;

public record AccessChannel(Long id, long providerId, AssetCode channelCode, String channelName,
        String baseUrl, Long credentialRefId, String description, AccessChannelStatus status,
        long rowVersion, Instant createdAt, Instant updatedAt) {

    public AccessChannel {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (providerId <= 0) throw new IllegalArgumentException("providerId must be positive");
        channelCode = Objects.requireNonNull(channelCode, "channelCode must not be null");
        channelName = required(channelName, "channelName", 200);
        baseUrl = baseUrl(baseUrl);
        if (credentialRefId != null && credentialRefId <= 0) {
            throw new IllegalArgumentException("credentialRefId must be positive");
        }
        description = optional(description, 1000);
        status = Objects.requireNonNull(status, "status must not be null");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }

    public static AccessChannel create(long providerId, AssetCode code, String name, String baseUrl,
            Long credentialRefId, String description) {
        return new AccessChannel(null, providerId, code, name, baseUrl, credentialRefId, description,
                AccessChannelStatus.ACTIVE, 0, null, null);
    }

    public AccessChannel revise(String name, String baseUrl, Long credentialRefId, String description,
            AccessChannelStatus status, long expectedVersion) {
        if (id == null) throw new IllegalStateException("unsaved channel cannot be revised");
        return new AccessChannel(id, providerId, channelCode, name, baseUrl, credentialRefId,
                description, status, expectedVersion, createdAt, updatedAt);
    }

    private static String baseUrl(String value) {
        String normalized = required(value, "baseUrl", 500);
        try {
            URI uri = new URI(normalized);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException("baseUrl must be an HTTP(S) origin without query or fragment");
            }
            return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid baseUrl", exception);
        }
    }

    private static String required(String value, String field, int max) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException(field + " must contain 1 to " + max + " characters");
        }
        return normalized;
    }

    private static String optional(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException("description is too long");
        return normalized;
    }
}
