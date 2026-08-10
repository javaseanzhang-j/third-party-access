package com.ftk.tpip.worker.globalimpact;

import io.micrometer.core.instrument.*;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
final class GlobalImpactWorkerMetrics {
    private final MeterRegistry registry;
    private final AtomicInteger inflight = new AtomicInteger();
    GlobalImpactWorkerMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("tpip.global.impact.worker.inflight", inflight, AtomicInteger::get).register(registry);
    }
    void poll(String outcome, int discovered) {
        Counter.builder("tpip.global.impact.worker.polls").tag("outcome", outcome).register(registry).increment();
        if (discovered > 0) Counter.builder("tpip.global.impact.worker.jobs.discovered").register(registry).increment(discovered);
    }
    void batch(String outcome, int claimed, Duration elapsed) {
        Counter.builder("tpip.global.impact.worker.batches").tag("outcome", outcome).register(registry).increment();
        if (claimed > 0) Counter.builder("tpip.global.impact.worker.items.claimed").register(registry).increment(claimed);
        Timer.builder("tpip.global.impact.worker.batch.duration").tag("outcome", outcome).register(registry).record(elapsed);
    }
    void rateLimited() { Counter.builder("tpip.global.impact.worker.rate.limited").register(registry).increment(); }
    void expiring() { Counter.builder("tpip.global.impact.worker.jobs.expiring").register(registry).increment(); }
    void enter() { inflight.incrementAndGet(); }
    void leave() { inflight.decrementAndGet(); }
}
