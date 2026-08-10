package com.ftk.tpip.provider.domain.exception;

public class EndpointCredentialMismatchException extends RuntimeException {

    public EndpointCredentialMismatchException(long contractId, long credentialRefId) {
        super("Credential " + credentialRefId
                + " is not ACTIVE or does not match the provider and environment of contract " + contractId);
    }
}
