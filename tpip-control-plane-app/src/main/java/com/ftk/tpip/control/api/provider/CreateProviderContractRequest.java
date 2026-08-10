package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.provider.domain.model.ProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateProviderContractRequest(
        @Positive long providerId,
        @NotBlank
        @Size(max = 180)
        @Pattern(regexp = "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")
        String contractCode,
        @NotBlank @Size(max = 200) String contractName,
        @NotNull ProtocolType protocolType,
        @Size(max = 1000) String description) {}
