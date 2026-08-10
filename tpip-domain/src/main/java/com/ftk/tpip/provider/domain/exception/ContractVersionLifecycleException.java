package com.ftk.tpip.provider.domain.exception;

public class ContractVersionLifecycleException extends RuntimeException {

    public ContractVersionLifecycleException(long versionId, String message) {
        super("Provider contract version " + versionId + ": " + message);
    }
}
