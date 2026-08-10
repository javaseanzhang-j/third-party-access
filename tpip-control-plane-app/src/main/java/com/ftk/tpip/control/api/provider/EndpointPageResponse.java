package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.EndpointPage;
import java.util.List;

public record EndpointPageResponse(
        List<EndpointResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages) {

    public static EndpointPageResponse from(EndpointPage endpointPage, ObjectMapper objectMapper) {
        return new EndpointPageResponse(
                endpointPage.items().stream()
                        .map(endpoint -> EndpointResponse.from(endpoint, objectMapper))
                        .toList(),
                endpointPage.page(),
                endpointPage.size(),
                endpointPage.totalElements(),
                endpointPage.totalPages());
    }
}
