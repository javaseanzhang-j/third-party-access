package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.provider.domain.model.EndpointHttpMethod;
import com.ftk.tpip.provider.domain.model.EndpointScheme;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateEndpointRevisionRequest(
        @Positive long providerContractId,
        @NotBlank
        @Size(max = 180)
        @Pattern(regexp = "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")
        String endpointCode,
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[a-z][a-z0-9_-]*$")
        String environmentCode,
        @NotNull EndpointScheme protocolScheme,
        @NotBlank @Size(max = 500) String baseUrl,
        @NotBlank @Size(max = 500) String resourcePath,
        @NotNull EndpointHttpMethod httpMethod,
        @Size(max = 100) String contentType,
        @Size(max = 32) String charsetName,
        @Positive Integer connectTimeoutMs,
        @Positive Integer readTimeoutMs,
        @Positive Integer totalTimeoutMs,
        @Positive Long credentialRefId,
        JsonNode networkConfig,
        JsonNode tlsConfig) {

    public CreateEndpointRevisionRequest {
        charsetName = charsetName == null || charsetName.isBlank() ? "UTF-8" : charsetName;
        connectTimeoutMs = connectTimeoutMs == null ? 1000 : connectTimeoutMs;
        readTimeoutMs = readTimeoutMs == null ? 3000 : readTimeoutMs;
        totalTimeoutMs = totalTimeoutMs == null ? 5000 : totalTimeoutMs;
    }
}
