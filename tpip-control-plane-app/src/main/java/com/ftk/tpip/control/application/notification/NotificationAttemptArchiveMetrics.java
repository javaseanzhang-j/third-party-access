package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.release.domain.repository.NotificationAttemptArchiveRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
final class NotificationAttemptArchiveMetrics {
    private final MeterRegistry registry;

    NotificationAttemptArchiveMetrics(MeterRegistry registry, NotificationAttemptArchiveRepository repository) {
        this.registry = registry;
        for (String status : new String[] {"CREATED", "STORED", "VERIFIED", "FAILED"}) {
            Gauge.builder("tpip.notification.attempt.archive.batches", repository,
                            value -> value.countBatches(status))
                    .tag("status", status).description("Notification attempt archive batches by status")
                    .register(registry);
        }
        Gauge.builder("tpip.notification.attempt.archive.verification.failures", repository,
                        value -> value.countVerificationFailuresSince(Instant.now().minus(Duration.ofDays(1))))
                .tag("window", "24h").description("Archive verification failures during the last 24 hours")
                .register(registry);
    }

    void archive(String outcome) {
        Counter.builder("tpip.notification.attempt.archive.automation")
                .tag("outcome", outcome).register(registry).increment();
    }

    void verification(String type, String result) {
        Counter.builder("tpip.notification.attempt.archive.verifications")
                .tag("type", type).tag("result", result).register(registry).increment();
    }

    void leaseMissed() {
        Counter.builder("tpip.notification.attempt.archive.lease.missed").register(registry).increment();
    }
}
