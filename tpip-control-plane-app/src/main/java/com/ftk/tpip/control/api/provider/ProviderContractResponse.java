package com.ftk.tpip.control.api.provider;

import com.ftk.tpip.provider.domain.model.ContractStatus;
import com.ftk.tpip.provider.domain.model.ProtocolType;
import com.ftk.tpip.provider.domain.model.ProviderContract;
import java.time.Instant;

public record ProviderContractResponse(
        long id,
        long providerId,
        String contractCode,
        String contractName,
        ProtocolType protocolType,
        String description,
        ContractStatus status,
        long rowVersion,
        Instant createdAt,
        Instant updatedAt) {

    public static ProviderContractResponse from(ProviderContract contract) {
        return new ProviderContractResponse(
                contract.id(),
                contract.providerId(),
                contract.contractCode().value(),
                contract.contractName(),
                contract.protocolType(),
                contract.description(),
                contract.status(),
                contract.rowVersion(),
                contract.createdAt(),
                contract.updatedAt());
    }
}
