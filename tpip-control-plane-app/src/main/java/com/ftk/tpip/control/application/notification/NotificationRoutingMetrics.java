package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
final class NotificationRoutingMetrics {
    NotificationRoutingMetrics(MeterRegistry registry, NotificationOutboxRepository outbox) {
        Gauge.builder("tpip.notification.routing.unmatched", outbox,
                        value -> value.countRoutingFailures())
                .description("Notification outbox events with no matching published route")
                .register(registry);
    }
}
