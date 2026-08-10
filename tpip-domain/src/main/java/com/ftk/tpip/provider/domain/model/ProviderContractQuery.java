package com.ftk.tpip.provider.domain.model;

public record ProviderContractQuery(
        Long providerId,
        String keyword,
        ContractStatus status,
        int offset,
        int limit) {

    public ProviderContractQuery {
        if (providerId != null && providerId <= 0) {
            throw new IllegalArgumentException("providerId must be positive");
        }
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (limit < 1 || limit > 200) {
            throw new IllegalArgumentException("limit must be between 1 and 200");
        }
    }
}
