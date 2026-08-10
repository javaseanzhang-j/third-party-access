package com.ftk.tpip.runtime.app.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.runtime.RuntimeInvocationObservation;
import com.ftk.tpip.runtime.RuntimeInvocationObserver;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MicrometerRuntimeInvocationObserver implements RuntimeInvocationObserver {
    private static final Logger AUDIT = LoggerFactory.getLogger("tpip.audit.runtime");
    private final MeterRegistry registry;
    private final ObjectMapper json;
    private final String instanceId;
    private final boolean auditEnabled;

    public MicrometerRuntimeInvocationObserver(MeterRegistry registry, ObjectMapper json,
            String instanceId, boolean auditEnabled) {
        this.registry = Objects.requireNonNull(registry);
        this.json = Objects.requireNonNull(json);
        this.instanceId = Objects.requireNonNull(instanceId);
        this.auditEnabled = auditEnabled;
    }

    @Override
    public void onCompleted(RuntimeInvocationObservation observation) {
        String outcome = observation.success() ? "success" : "failure";
        Counter.builder("tpip.runtime.invocations")
                .description("Completed TPIP runtime invocations")
                .tag("operation", observation.operationCode())
                .tag("environment", observation.environmentCode())
                .tag("deployment", observation.deploymentCode())
                .tag("bundleVersion", observation.bundleVersion())
                .tag("outcome", outcome)
                .tag("code", observation.resultCode())
                .register(registry).increment();
        Timer.builder("tpip.runtime.invocation.duration")
                .description("End-to-end TPIP runtime invocation duration")
                .tag("operation", observation.operationCode())
                .tag("environment", observation.environmentCode())
                .tag("deployment", observation.deploymentCode())
                .tag("bundleVersion", observation.bundleVersion())
                .tag("outcome", outcome)
                .register(registry).record(observation.duration());
        observation.stages().forEach(stage -> Timer.builder("tpip.runtime.stage.duration")
                .description("TPIP runtime pipeline stage duration")
                .tag("operation", observation.operationCode())
                .tag("stage", stage.stage().name())
                .tag("outcome", stage.success() ? "success" : "failure")
                .register(registry).record(stage.duration()));
        if (observation.providerStatusCode() != null) {
            Counter.builder("tpip.runtime.provider.responses")
                    .description("Provider HTTP responses observed by TPIP")
                    .tag("operation", observation.operationCode())
                    .tag("provider", observation.providerCode())
                    .tag("deployment", observation.deploymentCode())
                    .tag("statusClass", observation.providerStatusCode() / 100 + "xx")
                    .register(registry).increment();
        }
        if (auditEnabled) audit(observation);
    }

    private void audit(RuntimeInvocationObservation observation) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", "TPIP_RUNTIME_INVOCATION_COMPLETED");
        event.put("observedAt", observation.observedAt());
        event.put("instanceId", instanceId);
        event.put("requestId", observation.requestId());
        event.put("traceId", observation.traceId());
        event.put("operationCode", observation.operationCode());
        event.put("environmentCode", observation.environmentCode());
        event.put("deploymentCode", observation.deploymentCode());
        event.put("bundleVersion", observation.bundleVersion());
        event.put("providerCode", observation.providerCode());
        event.put("selectedBindingVersion", observation.selectedBindingVersion());
        event.put("routeVersionId", observation.routeVersionId());
        event.put("routingKeyHash", observation.routingKeyHash());
        event.put("providerStatusCode", observation.providerStatusCode());
        event.put("success", observation.success());
        event.put("resultCode", observation.resultCode());
        event.put("durationMs", observation.duration().toMillis());
        event.put("stages", observation.stages());
        try {
            AUDIT.info(json.writeValueAsString(event));
        } catch (Exception exception) {
            AUDIT.warn("Runtime audit serialization failed operation={} code={}",
                    observation.operationCode(), observation.resultCode());
        }
    }
}
