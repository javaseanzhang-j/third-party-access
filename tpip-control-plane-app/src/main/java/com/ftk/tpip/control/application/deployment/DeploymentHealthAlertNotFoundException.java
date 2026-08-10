package com.ftk.tpip.control.application.deployment;

public final class DeploymentHealthAlertNotFoundException extends RuntimeException {
    public DeploymentHealthAlertNotFoundException(long id) { super("Deployment health alert not found: " + id); }
}
