package com.ftk.tpip.integration.domain.model;

public record IntegrationBindingQuery(Long operationId, Long providerContractId, String keyword,
        BindingStatus status, int offset, int limit) {
    public IntegrationBindingQuery {
        if (operationId != null && operationId <= 0) throw new IllegalArgumentException("operationId must be positive");
        if (providerContractId != null && providerContractId <= 0) throw new IllegalArgumentException("providerContractId must be positive");
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (offset < 0) throw new IllegalArgumentException("offset must not be negative");
        if (limit < 1 || limit > 200) throw new IllegalArgumentException("limit must be between 1 and 200");
    }
}
