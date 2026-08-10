package com.ftk.tpip.control.application.integration;

public class IntegrationBindingVersionNotFoundException extends RuntimeException {
    public IntegrationBindingVersionNotFoundException(long bindingId, long versionId) {
        super("Binding version " + versionId + " does not exist for binding " + bindingId);
    }
}
