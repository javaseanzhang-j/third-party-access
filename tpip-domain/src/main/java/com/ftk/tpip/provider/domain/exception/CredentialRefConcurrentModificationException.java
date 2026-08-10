package com.ftk.tpip.provider.domain.exception;

public class CredentialRefConcurrentModificationException extends RuntimeException {

    public CredentialRefConcurrentModificationException(long id, long rowVersion) {
        super("Credential reference " + id + " was concurrently modified at rowVersion " + rowVersion);
    }
}
