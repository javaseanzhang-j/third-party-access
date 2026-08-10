package com.ftk.tpip.provider.domain.exception;

public class ProviderConcurrentModificationException extends RuntimeException {

    public ProviderConcurrentModificationException(long id, long expectedVersion) {
        super("Provider " + id + " was modified concurrently; expected rowVersion " + expectedVersion);
    }
}
