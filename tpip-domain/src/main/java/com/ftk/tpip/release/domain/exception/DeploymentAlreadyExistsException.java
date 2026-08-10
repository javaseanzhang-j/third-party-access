package com.ftk.tpip.release.domain.exception;

public class DeploymentAlreadyExistsException extends RuntimeException {
    public DeploymentAlreadyExistsException(String code) { super("Deployment already exists: " + code); }
}
