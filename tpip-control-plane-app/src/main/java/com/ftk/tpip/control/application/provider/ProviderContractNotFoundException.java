package com.ftk.tpip.control.application.provider;

public class ProviderContractNotFoundException extends RuntimeException {

    public ProviderContractNotFoundException(long id) {
        super("Provider contract not found: " + id);
    }
}
