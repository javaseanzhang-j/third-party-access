package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.provider.domain.model.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCredentialRefRequest(
        @Positive long providerId,
        @NotBlank
        @Size(max = 160)
        @Pattern(regexp = "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")
        String credentialCode,
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "^[a-z][a-z0-9_-]*$")
        String environmentCode,
        @NotNull CredentialType credentialType,
        @NotBlank @Size(max = 500) String secretUri,
        JsonNode secretMetadata) {}
