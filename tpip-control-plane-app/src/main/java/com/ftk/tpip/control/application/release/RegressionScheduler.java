package com.ftk.tpip.control.application.release;

import com.ftk.tpip.control.configuration.RegressionSchedulerProperties;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RegressionScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(RegressionScheduler.class);
    private final RegressionAutomationService service;
    private final RegressionSchedulerProperties properties;
    private final Clock clock;
    private final String leaseOwner;

    @Autowired
    public RegressionScheduler(RegressionAutomationService service, RegressionSchedulerProperties properties) {
        this(service, properties, Clock.systemUTC(), "regression-" + UUID.randomUUID());
    }

    RegressionScheduler(RegressionAutomationService service, RegressionSchedulerProperties properties,
            Clock clock, String leaseOwner) {
        this.service = service; this.properties = properties; this.clock = clock; this.leaseOwner = leaseOwner;
        properties.validate();
    }

    @Scheduled(fixedDelayString = "${tpip.regression-scheduler.poll-interval:1m}")
    public void executeDue() {
        if (!properties.isEnabled()) return;
        try {
            var result = service.executeDue(clock.instant(), properties.getBatchSize(), leaseOwner,
                    properties.getLease());
            if (result.claimed() > 0) LOG.info("TPIP_REGRESSION_CYCLE claimed={} completed={} drifted={} failed={} suspended={}",
                    result.claimed(), result.completed(), result.drifted(), result.failed(), result.suspended());
        } catch (RuntimeException failure) {
            LOG.error("TPIP_REGRESSION_CYCLE_FAILED reason={}", failure.getClass().getSimpleName());
        }
    }
}
