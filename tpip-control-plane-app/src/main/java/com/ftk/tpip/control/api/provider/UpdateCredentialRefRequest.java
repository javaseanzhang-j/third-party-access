package com.ftk.tpip.control.api.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.provider.domain.model.CredentialStatus;
import com.ftk.tpip.provider.domain.model.CredentialType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateCredentialRefRequest(
        @NotNull CredentialType credentialType,
        @NotBlank @Size(max = 500) String secretUri,
        JsonNode secretMetadata,
        @NotNull CredentialStatus status,
        @PositiveOrZero long rowVersion) {}
