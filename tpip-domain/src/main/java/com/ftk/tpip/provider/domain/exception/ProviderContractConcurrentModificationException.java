package com.ftk.tpip.provider.domain.exception;

public class ProviderContractConcurrentModificationException extends RuntimeException {

    public ProviderContractConcurrentModificationException(long id, long expectedVersion) {
        super("Provider contract " + id + " was modified concurrently; expected rowVersion " + expectedVersion);
    }
}
