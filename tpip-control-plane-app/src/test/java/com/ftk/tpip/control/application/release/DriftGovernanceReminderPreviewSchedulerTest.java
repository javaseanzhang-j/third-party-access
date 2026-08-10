package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.ftk.tpip.control.configuration.DriftGovernanceReminderPreviewProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DriftGovernanceReminderPreviewSchedulerTest {
    @Test
    void doesNothingWhilePreviewAutomationIsDisabled() {
        AtomicInteger calls = new AtomicInteger();
        var service = new DriftGovernanceReminderPreviewService(null, null) {
            @Override public PreviewResult createDueDrafts(Instant now, int executions, int batches,
                    String environment, String actor) {
                calls.incrementAndGet(); return new PreviewResult(0, 0, 0);
            }
        };
        var properties = new DriftGovernanceReminderPreviewProperties();
        var scheduler = new DriftGovernanceReminderPreviewScheduler(service, properties,
                Clock.fixed(Instant.parse("2026-08-09T00:00:00Z"), ZoneOffset.UTC));

        scheduler.createDueDrafts();

        assertEquals(0, calls.get());
    }
}
