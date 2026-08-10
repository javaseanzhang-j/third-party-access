package com.ftk.tpip.control.application.provider;

public class CredentialRefNotFoundException extends RuntimeException {

    public CredentialRefNotFoundException(long id) {
        super("Credential reference not found: " + id);
    }
}
