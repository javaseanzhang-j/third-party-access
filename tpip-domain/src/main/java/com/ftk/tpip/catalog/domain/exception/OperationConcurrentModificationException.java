package com.ftk.tpip.catalog.domain.exception;

public class OperationConcurrentModificationException extends RuntimeException {
    public OperationConcurrentModificationException(long id, long version) {
        super("Canonical operation " + id + " was concurrently modified at rowVersion " + version);
    }
}
