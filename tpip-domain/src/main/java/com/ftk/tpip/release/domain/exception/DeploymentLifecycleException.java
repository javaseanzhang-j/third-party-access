package com.ftk.tpip.release.domain.exception;

public class DeploymentLifecycleException extends RuntimeException {
    public DeploymentLifecycleException(long id, String reason) {
        super("Deployment " + id + " lifecycle conflict: " + reason);
    }
}
