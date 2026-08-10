package com.ftk.tpip.control.application.provider;

import com.ftk.tpip.provider.domain.model.Provider;
import java.util.List;

public record ProviderPage(List<Provider> items, int page, int size, long totalElements) {

    public ProviderPage {
        items = List.copyOf(items);
    }

    public long totalPages() {
        return totalElements == 0 ? 0 : (totalElements + size - 1) / size;
    }
}
