package com.ftk.tpip.provider.domain.model;

public record ProviderQuery(String keyword, ProviderStatus status, int offset, int limit) {

    public ProviderQuery {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
    }
}
