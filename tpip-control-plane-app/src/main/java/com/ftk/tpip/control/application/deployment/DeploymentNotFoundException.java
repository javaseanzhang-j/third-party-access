package com.ftk.tpip.control.application.deployment;

public class DeploymentNotFoundException extends RuntimeException {
    public DeploymentNotFoundException(long id) { super("Deployment does not exist: " + id); }
}
