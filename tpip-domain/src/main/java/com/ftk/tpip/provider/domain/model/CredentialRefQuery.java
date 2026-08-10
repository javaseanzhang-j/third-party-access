package com.ftk.tpip.provider.domain.model;

public record CredentialRefQuery(
        Long providerId,
        String environmentCode,
        String keyword,
        CredentialStatus status,
        int offset,
        int limit) {

    public CredentialRefQuery {
        if (providerId != null && providerId <= 0) {
            throw new IllegalArgumentException("providerId must be positive");
        }
        environmentCode = normalize(environmentCode);
        keyword = normalize(keyword);
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
