package com.ftk.tpip.runtime.app.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.runtime.RuntimeInvocationObservation;
import com.ftk.tpip.runtime.RuntimeStage;
import com.ftk.tpip.runtime.RuntimeStageObservation;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class MicrometerRuntimeInvocationObserverTest {
    @Test
    void recordsInvocationStageAndProviderMetricsWithoutHighCardinalityIds() {
        var registry = new SimpleMeterRegistry();
        var observer = new MicrometerRuntimeInvocationObserver(registry, new ObjectMapper(), "runtime-test", false);
        observer.onCompleted(new RuntimeInvocationObservation("request-unique", "trace-unique",
                "customer.lookup", "test", "customer.lookup.v1", "1.0.0", "provider.customer", 200,
                true, "SUCCESS", Duration.ofMillis(25),
                List.of(new RuntimeStageObservation(RuntimeStage.TRANSPORT, Duration.ofMillis(10), true)),
                Instant.parse("2026-08-08T00:00:00Z")));

        assertEquals(1.0, registry.get("tpip.runtime.invocations").counter().count());
        assertEquals(25, registry.get("tpip.runtime.invocation.duration").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        assertEquals(10, registry.get("tpip.runtime.stage.duration").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
        assertEquals(1.0, registry.get("tpip.runtime.provider.responses").counter().count());
        assertEquals(0, registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .filter(tag -> tag.getValue().contains("unique")).count());
    }
}
