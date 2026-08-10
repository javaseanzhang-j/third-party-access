package com.ftk.tpip.control.application.provider;

public class ProviderContractVersionNotFoundException extends RuntimeException {

    public ProviderContractVersionNotFoundException(long contractId, long versionId) {
        super("Provider contract version not found: contract=" + contractId + ", version=" + versionId);
    }
}
