package com.ftk.tpip.integration.domain.exception;

public class MappingDirectionAlreadyExistsException extends RuntimeException {
    public MappingDirectionAlreadyExistsException(long bindingId, String direction) {
        super("Binding " + bindingId + " already has mapping direction " + direction);
    }
}
