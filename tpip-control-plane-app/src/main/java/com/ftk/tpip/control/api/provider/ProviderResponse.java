package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.provider.domain.model.Provider;
import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.model.ProviderType;
import java.time.Instant;

public record ProviderResponse(
        long id,
        String providerCode,
        String providerName,
        ProviderType providerType,
        String description,
        String ownerCode,
        ProviderStatus status,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    public static ProviderResponse from(Provider provider) {
        return new ProviderResponse(
                provider.id(),
                provider.providerCode().value(),
                provider.providerName(),
                provider.providerType(),
                provider.description(),
                provider.ownerCode(),
                provider.status(),
                provider.rowVersion(),
                provider.createdAt(),
                provider.updatedAt());
    }
}
