package com.ftk.tpip.control.application.integration;
public class IntegrationBindingNotFoundException extends RuntimeException {
    public IntegrationBindingNotFoundException(long id){super("Integration binding not found: "+id);}
}
