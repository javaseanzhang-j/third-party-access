package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ftk.tpip.control.configuration.RegressionSchedulerProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RegressionSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void doesNothingWhileGlobalAutomationIsDisabled() {
        AtomicInteger calls = new AtomicInteger();
        var service = new RegressionAutomationService(null, null, null) {
            @Override public CycleResult executeDue(Instant now, int limit, String owner, java.time.Duration lease) {
                calls.incrementAndGet(); return new CycleResult(0, 0, 0, 0, 0);
            }
        };
        var properties = new RegressionSchedulerProperties();
        var scheduler = new RegressionScheduler(service, properties, Clock.fixed(NOW, ZoneOffset.UTC), "worker-1");

        scheduler.executeDue();

        assertEquals(0, calls.get());
    }
}
