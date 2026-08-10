package com.ftk.tpip.control.application.deployment;

public final class HealthAutomationUnauthorizedException extends RuntimeException {
    public HealthAutomationUnauthorizedException() {
        super("health automation authentication failed");
    }
}
