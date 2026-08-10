package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.provider.domain.model.ProviderStatus;
import com.ftk.tpip.provider.domain.model.ProviderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateProviderRequest(
        @NotBlank @Size(max = 200) String providerName,
        @NotNull ProviderType providerType,
        @Size(max = 1000) String description,
        @NotBlank @Size(max = 100) String ownerCode,
        @NotNull ProviderStatus status,
        @PositiveOrZero long rowVersion) {}
