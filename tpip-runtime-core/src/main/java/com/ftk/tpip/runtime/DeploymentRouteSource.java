package com.ftk.tpip.runtime;

@FunctionalInterface
public interface DeploymentRouteSource {
    DeploymentRouteSnapshot fetch(String operationCode, String environmentCode);
}
