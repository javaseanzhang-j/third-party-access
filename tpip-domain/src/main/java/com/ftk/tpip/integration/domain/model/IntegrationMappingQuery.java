package com.ftk.tpip.integration.domain.model;

public record IntegrationMappingQuery(Long bindingId, MappingAssetDirection direction, String keyword,
        MappingStatus status, int offset, int limit) {
    public IntegrationMappingQuery {
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }
}
