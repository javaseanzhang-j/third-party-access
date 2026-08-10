package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.control.application.provider.ProviderContractPage;
import java.util.List;

public record ProviderContractPageResponse(
        List<ProviderContractResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    public static ProviderContractPageResponse from(ProviderContractPage page) {
        return new ProviderContractPageResponse(
                page.items().stream().map(ProviderContractResponse::from).toList(),
                page.page(),
                page.size(),
                page.totalElements(),
                page.totalPages());
    }
}
