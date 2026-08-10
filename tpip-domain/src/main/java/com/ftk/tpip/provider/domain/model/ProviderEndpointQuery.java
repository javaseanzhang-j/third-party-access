package com.ftk.tpip.provider.domain.model;

public record ProviderEndpointQuery(
        Long providerContractId,
        String endpointCode,
        String environmentCode,
        EndpointLifecycleStatus lifecycleStatus,
        boolean latestOnly,
        int offset,
        int limit) {

    public ProviderEndpointQuery {
        if (providerContractId != null && providerContractId <= 0) {
            throw new IllegalArgumentException("providerContractId must be positive");
        }
        endpointCode = normalize(endpointCode);
        environmentCode = normalize(environmentCode);
        if (offset < 0 || limit < 1 || limit > 200) {
            throw new IllegalArgumentException("invalid endpoint page window");
        }
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
