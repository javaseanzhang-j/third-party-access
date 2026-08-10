package com.ftk.tpip.control.application.integration;

public class IntegrationMappingVersionNotFoundException extends RuntimeException {
    public IntegrationMappingVersionNotFoundException(long mappingId, long versionId) {
        super("Mapping version " + versionId + " was not found under mapping " + mappingId);
    }
}
