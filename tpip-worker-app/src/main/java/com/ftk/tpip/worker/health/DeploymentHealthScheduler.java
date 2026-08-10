package com.ftk.tpip.worker.health;

import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class DeploymentHealthScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(DeploymentHealthScheduler.class);
    private final ControlPlaneHealthClient control;
    private final PrometheusHealthEvidenceClient prometheus;
    private final HealthWindowGuard guard;
    private final HealthWorkerProperties properties;
    private final Clock clock;

    DeploymentHealthScheduler(ControlPlaneHealthClient control, PrometheusHealthEvidenceClient prometheus,
            HealthWindowGuard guard, HealthWorkerProperties properties, Clock clock) {
        this.control = control;
        this.prometheus = prometheus;
        this.guard = guard;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tpip.health-worker.poll-interval:30s}")
    public void evaluateActiveCanaries() {
        if (!properties.isEnabled()) return;
        Instant windowEnd = alignedWindowEnd(clock.instant());
        Instant windowStart = windowEnd.minus(properties.getEvaluationWindow());
        for (HealthCandidate candidate : control.candidates()) {
            if (candidate.activatedAt() == null || candidate.activatedAt().isAfter(windowStart)) continue;
            evaluate(candidate, windowStart, windowEnd);
        }
    }

    private void evaluate(HealthCandidate candidate, Instant windowStart, Instant windowEnd) {
        if (!guard.tryAcquire(candidate.deploymentId(), windowEnd)) return;
        try {
            HealthEvidence evidence = prometheus.collect(candidate, windowEnd);
            var result = control.evaluate(candidate.deploymentId(), windowStart, windowEnd, evidence);
            guard.complete(candidate.deploymentId());
            LOG.info("TPIP_HEALTH_WINDOW_EVALUATED deploymentId={} deployment={} windowEnd={} samples={} "
                            + "failures={} p95LatencyMs={} decision={} action={} rollbackDeploymentId={}",
                    candidate.deploymentId(), candidate.deploymentCode(), windowEnd, evidence.sampleCount(),
                    evidence.failureCount(), evidence.p95LatencyMs(), result.decision(), result.action(),
                    result.rollbackDeploymentId());
        } catch (RuntimeException exception) {
            guard.release(candidate.deploymentId(), windowEnd);
            LOG.warn("TPIP_HEALTH_WINDOW_FAILED deploymentId={} deployment={} windowEnd={} reason={}",
                    candidate.deploymentId(), candidate.deploymentCode(), windowEnd, exception.getMessage());
        }
    }

    private Instant alignedWindowEnd(Instant now) {
        long seconds = properties.getEvaluationWindow().toSeconds();
        long aligned = Math.floorDiv(now.getEpochSecond(), seconds) * seconds;
        return Instant.ofEpochSecond(aligned);
    }
}
