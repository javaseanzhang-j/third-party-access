package com.ftk.tpip.integration.domain.exception;

public class MappingVersionLifecycleException extends RuntimeException {
    public MappingVersionLifecycleException(long id, String reason) {
        super("Mapping version " + id + " lifecycle conflict: " + reason);
    }
}
