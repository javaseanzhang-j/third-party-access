package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProviderContractVersionRequest(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$")
        String semanticVersion,
        JsonNode requestSchema,
        JsonNode responseSchema,
        JsonNode errorSchema,
        JsonNode callbackSchema,
        JsonNode examples) {}
