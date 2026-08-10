package com.ftk.tpip.integration.domain.exception;

public class BindingVersionLifecycleException extends RuntimeException {
    public BindingVersionLifecycleException(long versionId, String reason) {
        super("Binding version " + versionId + " lifecycle conflict: " + reason);
    }
}
