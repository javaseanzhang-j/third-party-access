package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.control.configuration.NotificationAttemptArchiveProperties;
import com.ftk.tpip.release.domain.repository.NotificationAttemptArchiveRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationAttemptArchiveScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationAttemptArchiveScheduler.class);
    private static final String LEASE = "notification-attempt-archive-automation";
    private static final String ACTOR = "system-notification-attempt-archive";
    private final NotificationAttemptArchiveApplicationService service;
    private final NotificationAttemptArchiveRepository repository;
    private final NotificationAttemptArchiveProperties properties;
    private final NotificationAttemptArchiveMetrics metrics;
    private final Clock clock;
    private final String owner;

    @Autowired
    public NotificationAttemptArchiveScheduler(NotificationAttemptArchiveApplicationService service,
            NotificationAttemptArchiveRepository repository, NotificationAttemptArchiveProperties properties,
            NotificationAttemptArchiveMetrics metrics) {
        this(service, repository, properties, metrics, Clock.systemUTC(), UUID.randomUUID().toString());
    }

    NotificationAttemptArchiveScheduler(NotificationAttemptArchiveApplicationService service,
            NotificationAttemptArchiveRepository repository, NotificationAttemptArchiveProperties properties,
            NotificationAttemptArchiveMetrics metrics, Clock clock, String owner) {
        this.service = service; this.repository = repository; this.properties = properties;
        this.metrics = metrics; this.clock = clock; this.owner = owner; properties.validate();
    }

    @Scheduled(fixedDelayString = "${tpip.notification-attempt-archive.automation-poll-interval:1h}")
    public void runCycle() {
        if (!properties.isAutomationEnabled()) return;
        Instant now = clock.instant();
        if (!repository.tryAcquireLease(LEASE, owner, now, now.plus(properties.getAutomationLease()))) {
            metrics.leaseMissed(); return;
        }
        try {
            archiveCompletedWindows(now);
            if (properties.isVerificationDrillEnabled()) runVerificationDrills(now);
        } finally {
            repository.releaseLease(LEASE, owner, clock.instant());
        }
    }

    private void archiveCompletedWindows(Instant now) {
        Duration window = properties.getAutomationWindow();
        Instant completedEnd = completedWindowEnd(now, window, properties.getAutomationCompletionDelay());
        long windows = Math.max(1, divideRoundingUp(properties.getAutomationLookback().toMillis(), window.toMillis()));
        int changed = 0;
        for (long offset = windows - 1; offset >= 0 && changed < properties.getAutomationMaximumBatchesPerCycle();
                offset--) {
            Instant end = completedEnd.minus(window.multipliedBy(offset));
            Instant start = end.minus(window);
            for (String environment : properties.getAutomationEnvironments()) {
                if (changed >= properties.getAutomationMaximumBatchesPerCycle()) break;
                try {
                    var result = service.archiveCompletedWindowIfNecessary(environment.trim(), start, end, ACTOR);
                    if (result.isPresent()) {
                        changed++; metrics.archive(result.get().status());
                    }
                } catch (RuntimeException exception) {
                    metrics.archive("FAILED");
                    LOG.error("Notification attempt archive automation failed for environment {} window [{},{}): {}",
                            environment, start, end, exception.getClass().getSimpleName());
                }
            }
        }
    }

    private void runVerificationDrills(Instant now) {
        var candidates = service.verificationCandidates(now.minus(properties.getVerificationDrillInterval()),
                properties.getVerificationMaximumBatchesPerCycle());
        for (var batch : candidates) {
            try {
                service.audit(batch.id(), "DRILL", ACTOR); metrics.verification("DRILL", "PASSED");
            } catch (RuntimeException exception) {
                metrics.verification("DRILL", "FAILED");
                LOG.error("Notification attempt archive verification drill failed for batch {}: {}",
                        batch.id(), exception.getClass().getSimpleName());
            }
        }
    }

    static Instant completedWindowEnd(Instant now, Duration window, Duration delay) {
        long windowMillis = window.toMillis();
        long effectiveMillis = now.minus(delay).toEpochMilli();
        return Instant.ofEpochMilli(Math.floorDiv(effectiveMillis, windowMillis) * windowMillis);
    }

    private static long divideRoundingUp(long value, long divisor) {
        return Math.floorDiv(value, divisor) + (value % divisor == 0 ? 0 : 1);
    }
}
