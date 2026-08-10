package com.ftk.tpip.control.application.release;

import com.ftk.tpip.control.configuration.DriftGovernanceReminderPreviewProperties;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DriftGovernanceReminderPreviewScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(DriftGovernanceReminderPreviewScheduler.class);
    private final DriftGovernanceReminderPreviewService service;
    private final DriftGovernanceReminderPreviewProperties properties;
    private final Clock clock;

    @Autowired
    public DriftGovernanceReminderPreviewScheduler(DriftGovernanceReminderPreviewService service,
            DriftGovernanceReminderPreviewProperties properties) {
        this(service, properties, Clock.systemUTC());
    }

    DriftGovernanceReminderPreviewScheduler(DriftGovernanceReminderPreviewService service,
            DriftGovernanceReminderPreviewProperties properties, Clock clock) {
        this.service = service; this.properties = properties; this.clock = clock; properties.validate();
    }

    @Scheduled(fixedDelayString = "${tpip.drift-governance-reminder-preview.poll-interval:5m}")
    public void createDueDrafts() {
        if (!properties.isEnabled()) return;
        try {
            var result = service.createDueDrafts(clock.instant(), properties.getMaximumExecutionsPerCycle(),
                    properties.getMaximumBatchesPerCycle(), properties.getEnvironmentCode(), properties.getActor());
            if (result.eligibleExecutions() > 0)
                LOG.info("TPIP_DRIFT_REMINDER_PREVIEW eligible={} created={} conflicts={}",
                        result.eligibleExecutions(), result.createdBatches(), result.conflicts());
        } catch (RuntimeException failure) {
            LOG.error("TPIP_DRIFT_REMINDER_PREVIEW_FAILED reason={}", failure.getClass().getSimpleName());
        }
    }
}
