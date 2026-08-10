package com.ftk.tpip.provider.domain.exception;

public class EndpointLifecycleException extends RuntimeException {

    public EndpointLifecycleException(long endpointId, String message) {
        super("Endpoint " + endpointId + ": " + message);
    }
}
