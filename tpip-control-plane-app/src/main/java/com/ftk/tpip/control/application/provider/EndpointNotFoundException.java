package com.ftk.tpip.control.application.provider;

public class EndpointNotFoundException extends RuntimeException {

    public EndpointNotFoundException(long id) {
        super("Endpoint not found: " + id);
    }
}
