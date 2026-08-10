package com.ftk.tpip.runtime;

@FunctionalInterface
public interface DeploymentResolver {
    ResolvedDeployment resolve(String operationCode, String environmentCode, String routingKey);
}
