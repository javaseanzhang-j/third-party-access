package com.ftk.tpip.control.application.integration;

public class IntegrationMappingNotFoundException extends RuntimeException {
    public IntegrationMappingNotFoundException(long id) { super("Integration mapping not found: " + id); }
}
