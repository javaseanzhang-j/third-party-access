package com.ftk.tpip.provider.domain.exception;

public class ProviderContractVersionAlreadyExistsException extends RuntimeException {

    public ProviderContractVersionAlreadyExistsException(long contractId, String semanticVersion) {
        super("Provider contract " + contractId + " already has semantic version " + semanticVersion);
    }
}
