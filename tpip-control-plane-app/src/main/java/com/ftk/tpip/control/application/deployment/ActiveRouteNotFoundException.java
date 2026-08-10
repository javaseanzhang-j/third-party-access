package com.ftk.tpip.control.application.deployment;

public class ActiveRouteNotFoundException extends RuntimeException {
    public ActiveRouteNotFoundException(String operationCode, String environmentCode) {
        super("No active deployment route for " + operationCode + "@" + environmentCode);
    }
}
