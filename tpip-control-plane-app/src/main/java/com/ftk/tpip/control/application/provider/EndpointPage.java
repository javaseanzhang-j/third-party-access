package com.ftk.tpip.control.application.provider;

import com.ftk.tpip.provider.domain.model.ProviderEndpoint;
import java.util.List;

public record EndpointPage(List<ProviderEndpoint> items, int page, int size, long totalElements) {

    public EndpointPage {
        items = List.copyOf(items);
    }

    public long totalPages() {
        return totalElements == 0 ? 0 : (totalElements + size - 1) / size;
    }
}
