package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.provider.domain.model.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateProviderRequest(
        @NotBlank
        @Size(max = 128)
        @Pattern(regexp = "^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$")
        String providerCode,
        @NotBlank @Size(max = 200) String providerName,
        @NotNull ProviderType providerType,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 100) String ownerCode) {}
