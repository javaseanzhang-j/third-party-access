package com.ftk.tpip.control.application.release;

import com.ftk.tpip.control.configuration.WorkspaceVerificationProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceVerificationRecoveryScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(WorkspaceVerificationRecoveryScheduler.class);
    private final WorkspaceVerificationRecoveryService recovery;
    private final WorkspaceVerificationProperties properties;
    private final Clock clock;

    @Autowired
    public WorkspaceVerificationRecoveryScheduler(WorkspaceVerificationRecoveryService recovery,
            WorkspaceVerificationProperties properties) {
        this(recovery, properties, Clock.systemUTC());
    }

    WorkspaceVerificationRecoveryScheduler(WorkspaceVerificationRecoveryService recovery,
            WorkspaceVerificationProperties properties, Clock clock) {
        this.recovery = recovery; this.properties = properties; this.clock = clock; properties.validate();
    }

    @Scheduled(fixedDelayString = "${tpip.workspace-verification.recovery-poll-interval:1m}")
    public void recover() {
        if (!properties.isRecoveryEnabled()) return;
        Instant now = clock.instant();
        try {
            int count = recovery.recoverStale(now.minus(properties.getTimeout()),
                    properties.getRecoveryBatchSize(), now);
            if (count > 0) LOG.warn("TPIP_WORKSPACE_VERIFICATION_STALE_RECOVERED count={}", count);
        } catch (RuntimeException failure) {
            LOG.error("TPIP_WORKSPACE_VERIFICATION_RECOVERY_FAILED reason={}", failure.getClass().getSimpleName());
        }
    }
}
