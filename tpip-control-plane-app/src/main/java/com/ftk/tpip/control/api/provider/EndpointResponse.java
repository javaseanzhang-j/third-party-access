package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.EndpointLifecycleStatus;
import com.ftk.tpip.provider.domain.model.EndpointScheme;
import com.ftk.tpip.provider.domain.model.ProviderEndpoint;
import java.time.Instant;

public record EndpointResponse(
        long id,
        long providerContractId,
        String endpointCode,
        String environmentCode,
        int revisionNo,
        EndpointScheme protocolScheme,
        String baseUrl,
        String resourcePath,
        EndpointHttpMethod httpMethod,
        String contentType,
        String charsetName,
        int connectTimeoutMs,
        int readTimeoutMs,
        int totalTimeoutMs,
        Long credentialRefId,
        JsonNode networkConfig,
        JsonNode tlsConfig,
        EndpointLifecycleStatus lifecycleStatus,
        String contentChecksum,
        Instant publishedAt,
        Instant createdAt) {

    public static EndpointResponse from(ProviderEndpoint endpoint, ObjectMapper objectMapper) {
        return new EndpointResponse(
                endpoint.id(),
                endpoint.providerContractId(),
                endpoint.endpointCode().value(),
                endpoint.environmentCode(),
                endpoint.revisionNo(),
                endpoint.protocolScheme(),
                endpoint.baseUrl(),
                endpoint.resourcePath(),
                endpoint.httpMethod(),
                endpoint.contentType(),
                endpoint.charsetName(),
                endpoint.connectTimeoutMs(),
                endpoint.readTimeoutMs(),
                endpoint.totalTimeoutMs(),
                endpoint.credentialRefId(),
                read(endpoint.networkConfig(), objectMapper),
                read(endpoint.tlsConfig(), objectMapper),
                endpoint.lifecycleStatus(),
                endpoint.contentChecksum(),
                endpoint.publishedAt(),
                endpoint.createdAt());
    }

    private static JsonNode read(String value, ObjectMapper objectMapper) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored endpoint JSON is invalid", exception);
        }
    }
}
