package com.ftk.tpip.provider.domain.exception;

public class ProviderContractCodeAlreadyExistsException extends RuntimeException {

    public ProviderContractCodeAlreadyExistsException(String contractCode) {
        super("Provider contract code already exists: " + contractCode);
    }
}
