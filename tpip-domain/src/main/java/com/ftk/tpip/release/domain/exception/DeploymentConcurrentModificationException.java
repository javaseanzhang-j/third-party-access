package com.ftk.tpip.release.domain.exception;

public class DeploymentConcurrentModificationException extends RuntimeException {
    public DeploymentConcurrentModificationException(long id, long rowVersion) {
        super("Deployment " + id + " was concurrently modified; expected row version " + rowVersion);
    }
}
