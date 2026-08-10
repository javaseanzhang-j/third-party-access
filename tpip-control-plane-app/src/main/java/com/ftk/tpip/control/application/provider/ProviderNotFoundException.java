package com.ftk.tpip.control.application.provider;

public class ProviderNotFoundException extends RuntimeException {

    public ProviderNotFoundException(long id) {
        super("Provider not found: " + id);
    }
}
