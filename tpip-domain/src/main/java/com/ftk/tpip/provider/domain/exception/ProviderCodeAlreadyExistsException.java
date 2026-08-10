package com.ftk.tpip.provider.domain.exception;

public class ProviderCodeAlreadyExistsException extends RuntimeException {

    public ProviderCodeAlreadyExistsException(String providerCode) {
        super("Provider code already exists: " + providerCode);
    }
}
