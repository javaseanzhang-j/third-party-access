package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.provider.domain.model.ContractStatus;
import com.ftk.tpip.provider.domain.model.ProtocolType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record UpdateProviderContractRequest(
        @NotBlank @Size(max = 200) String contractName,
        @NotNull ProtocolType protocolType,
        @Size(max = 1000) String description,
        @NotNull ContractStatus status,
        @PositiveOrZero long rowVersion) {}
