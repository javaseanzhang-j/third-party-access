package com.ftk.tpip.integration.domain.exception;

public class BindingCodeAlreadyExistsException extends RuntimeException {
    public BindingCodeAlreadyExistsException(String code) { super("Integration binding already exists: " + code); }
}
