package com.ftk.tpip.integration.domain.exception;

public class MappingCodeAlreadyExistsException extends RuntimeException {
    public MappingCodeAlreadyExistsException(String code) { super("Mapping code already exists: " + code); }
}
