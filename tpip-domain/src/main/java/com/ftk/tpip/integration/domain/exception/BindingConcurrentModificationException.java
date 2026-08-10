package com.ftk.tpip.integration.domain.exception;

public class BindingConcurrentModificationException extends RuntimeException {
    public BindingConcurrentModificationException(long id, long version) {
        super("Integration binding " + id + " was concurrently modified at rowVersion " + version);
    }
}
