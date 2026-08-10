package com.ftk.tpip.worker.notification;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
final class NotificationDispatcherMetrics {
    private final MeterRegistry registry;

    NotificationDispatcherMetrics(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry);
    }

    void claimSucceeded(int count) {
        Counter.builder("tpip.notification.claim.cycles")
                .description("Notification dispatcher claim cycles")
                .tag("outcome", "success").register(registry).increment();
        if (count > 0) Counter.builder("tpip.notification.deliveries.claimed")
                .description("Channel delivery tasks claimed by notification workers")
                .register(registry).increment(count);
    }

    void claimFailed(String errorCode) {
        Counter.builder("tpip.notification.claim.cycles")
                .description("Notification dispatcher claim cycles")
                .tag("outcome", "failure").tag("code", errorCode).register(registry).increment();
    }

    void delivery(String channelCode, String outcome, String code, Duration duration) {
        Counter.builder("tpip.notification.deliveries")
                .description("Completed channel notification delivery attempts")
                .tag("channel", channelCode).tag("outcome", outcome).tag("code", code)
                .register(registry).increment();
        Timer.builder("tpip.notification.delivery.duration")
                .description("Channel notification delivery attempt duration")
                .tag("channel", channelCode).tag("outcome", outcome)
                .register(registry).record(duration);
    }
}
