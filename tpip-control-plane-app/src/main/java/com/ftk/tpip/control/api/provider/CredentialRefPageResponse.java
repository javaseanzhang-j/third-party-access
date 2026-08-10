package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CredentialRefPage;
import java.util.List;

public record CredentialRefPageResponse(
        List<CredentialRefResponse> items,
        int page,
        int size,
        long totalElements) {

    public static CredentialRefPageResponse from(CredentialRefPage page, ObjectMapper objectMapper) {
        return new CredentialRefPageResponse(
                page.items().stream()
                        .map(item -> CredentialRefResponse.from(item, objectMapper))
                        .toList(),
                page.page(),
                page.size(),
                page.totalElements());
    }
}
