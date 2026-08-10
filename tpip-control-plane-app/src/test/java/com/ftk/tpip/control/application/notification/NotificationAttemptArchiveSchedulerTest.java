package com.ftk.tpip.control.application.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.provider.CanonicalJsonService;
import com.ftk.tpip.control.configuration.NotificationAttemptArchiveProperties;
import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import com.ftk.tpip.release.domain.model.NotificationDeliveryAttemptEvidence;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class NotificationAttemptArchiveSchedulerTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void createsAndVerifiesOnlyCompletedWindowWhileHoldingLease() {
        var repository = new NotificationAttemptArchiveApplicationServiceTest.FakeRepository();
        Instant start = NOW.minus(Duration.ofHours(2));
        repository.evidence.add(new NotificationDeliveryAttemptEvidence(11, 101, "default", 1,
                "WEBHOOK", "ops", 7L, "SUCCESS", null, null, null, false, start.plusSeconds(1)));
        var store = new NotificationAttemptArchiveApplicationServiceTest.FakeStore();
        var properties = new NotificationAttemptArchiveProperties();
        properties.setAutomationEnabled(true); properties.setAutomationWindow(Duration.ofHours(1));
        properties.setAutomationCompletionDelay(Duration.ofHours(1));
        properties.setAutomationLookback(Duration.ofHours(1));
        properties.setAutomationMaximumBatchesPerCycle(1); properties.setVerificationDrillEnabled(false);
        ObjectMapper json = new ObjectMapper();
        var service = new NotificationAttemptArchiveApplicationService(repository, store, properties,
                new NotificationDeliveryProperties(), new CanonicalJsonService(json), json,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var metrics = new NotificationAttemptArchiveMetrics(new SimpleMeterRegistry(), repository);
        var scheduler = new NotificationAttemptArchiveScheduler(service, repository, properties, metrics,
                Clock.fixed(NOW, ZoneOffset.UTC), "test-owner");

        scheduler.runCycle();

        assertEquals("VERIFIED", repository.batch.status());
        assertEquals(start, repository.batch.windowStart());
        assertEquals(1, repository.verifications.size());
    }

    @Test
    void alignsCompletedWindowAfterSafetyDelay() {
        assertEquals(Instant.parse("2026-08-08T22:00:00Z"),
                NotificationAttemptArchiveScheduler.completedWindowEnd(
                        Instant.parse("2026-08-08T23:45:00Z"), Duration.ofHours(1), Duration.ofHours(1)));
    }
}
