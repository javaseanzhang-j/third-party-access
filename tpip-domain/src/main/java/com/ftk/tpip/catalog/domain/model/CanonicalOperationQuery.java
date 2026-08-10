package com.ftk.tpip.catalog.domain.model;

public record CanonicalOperationQuery(Long capabilityId, String keyword, OperationStatus status, int offset, int limit) {
    public CanonicalOperationQuery {
        if (capabilityId != null && capabilityId <= 0) throw new IllegalArgumentException("capabilityId must be positive");
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }
}
