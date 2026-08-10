package com.ftk.tpip.control.application.provider;

import com.ftk.tpip.provider.domain.model.ProviderContract;
import java.util.List;

public record ProviderContractPage(
        List<ProviderContract> items,
        int page,
        int size,
        long totalElements) {

    public ProviderContractPage {
        items = List.copyOf(items);
    }

    public long totalPages() {
        return totalElements == 0 ? 0 : (totalElements + size - 1) / size;
    }
}
