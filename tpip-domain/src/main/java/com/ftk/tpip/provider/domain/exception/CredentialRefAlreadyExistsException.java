package com.ftk.tpip.provider.domain.exception;

public class CredentialRefAlreadyExistsException extends RuntimeException {

    public CredentialRefAlreadyExistsException(String code, String environmentCode) {
        super("Credential reference already exists: " + code + " in " + environmentCode);
    }
}
