package com.ftk.tpip.integration.domain.exception;

public class MappingConcurrentModificationException extends RuntimeException {
    public MappingConcurrentModificationException(long id, long version) {
        super("Mapping " + id + " was concurrently modified; expected row version " + version);
    }
}
