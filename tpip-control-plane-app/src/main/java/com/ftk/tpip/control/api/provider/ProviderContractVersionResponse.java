package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.provider.domain.model.ContractLifecycleStatus;
import com.ftk.tpip.provider.domain.model.ProviderContractVersion;
import java.time.Instant;

public record ProviderContractVersionResponse(
        long id,
        long providerContractId,
        int versionNo,
        String semanticVersion,
        JsonNode requestSchema,
        JsonNode responseSchema,
        JsonNode errorSchema,
        JsonNode callbackSchema,
        JsonNode examples,
        String contentChecksum,
        ContractLifecycleStatus lifecycleStatus,
        Instant publishedAt,
        Instant createdAt) {

    public static ProviderContractVersionResponse from(
            ProviderContractVersion version, ObjectMapper objectMapper) {
        return new ProviderContractVersionResponse(
                version.id(),
                version.providerContractId(),
                version.versionNo(),
                version.semanticVersion().toString(),
                read(version.requestSchema(), objectMapper),
                read(version.responseSchema(), objectMapper),
                read(version.errorSchema(), objectMapper),
                read(version.callbackSchema(), objectMapper),
                read(version.examples(), objectMapper),
                version.contentChecksum(),
                version.lifecycleStatus(),
                version.publishedAt(),
                version.createdAt());
    }

    private static JsonNode read(String value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored contract JSON is invalid", exception);
        }
    }
}
