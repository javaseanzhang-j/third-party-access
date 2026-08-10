package com.ftk.tpip.catalog.domain.exception;

public class OperationCodeAlreadyExistsException extends RuntimeException {
    public OperationCodeAlreadyExistsException(String code) { super("Canonical operation already exists: " + code); }
}
