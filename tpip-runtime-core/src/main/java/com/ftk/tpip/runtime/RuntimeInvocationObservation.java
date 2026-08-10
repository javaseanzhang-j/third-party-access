package com.ftk.tpip.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public record RuntimeInvocationObservation(
        String requestId,
        String traceId,
        String operationCode,
        String environmentCode,
        String deploymentCode,
        String bundleVersion,
        String providerCode,
        Integer providerStatusCode,
        boolean success,
        String resultCode,
        Duration duration,
        String selectedBindingVersion,
        Long routeVersionId,
        String routingKeyHash,
        List<RuntimeStageObservation> stages,
        Instant observedAt) {
    public RuntimeInvocationObservation {
        stages = stages == null ? List.of() : List.copyOf(stages);
    }
    public RuntimeInvocationObservation(String requestId,String traceId,String operationCode,String environmentCode,
            String deploymentCode,String bundleVersion,String providerCode,Integer providerStatusCode,boolean success,
            String resultCode,Duration duration,List<RuntimeStageObservation> stages,Instant observedAt){
        this(requestId,traceId,operationCode,environmentCode,deploymentCode,bundleVersion,providerCode,
                providerStatusCode,success,resultCode,duration,null,null,null,stages,observedAt);
    }
}
