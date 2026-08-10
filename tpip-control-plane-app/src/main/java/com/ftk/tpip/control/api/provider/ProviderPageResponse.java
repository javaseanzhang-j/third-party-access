package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.control.application.provider.ProviderPage;
import java.util.List;

public record ProviderPageResponse(
        List<ProviderResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    public static ProviderPageResponse from(ProviderPage providerPage) {
        return new ProviderPageResponse(
                providerPage.items().stream().map(ProviderResponse::from).toList(),
                providerPage.page(),
                providerPage.size(),
                providerPage.totalElements(),
                providerPage.totalPages());
    }
}
