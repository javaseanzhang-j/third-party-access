package com.ftk.tpip.provider.domain.exception;

public class EndpointCodeOwnershipException extends RuntimeException {

    public EndpointCodeOwnershipException(String endpointCode, String environmentCode) {
        super("Endpoint code " + endpointCode + " in environment " + environmentCode
                + " already belongs to another provider contract");
    }
}
