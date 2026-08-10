package com.ftk.tpip.worker.globalimpact;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class GlobalImpactWorkerScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalImpactWorkerScheduler.class);
    private final GlobalImpactControlClient control;
    private final GlobalImpactWorkerProperties properties;
    private final LocalBatchRateLimiter limiter;
    private final GlobalImpactWorkerMetrics metrics;
    private final ExecutorService executor;
    private final Clock clock;
    private final AtomicBoolean polling = new AtomicBoolean();

    GlobalImpactWorkerScheduler(GlobalImpactControlClient control, GlobalImpactWorkerProperties properties,
            LocalBatchRateLimiter limiter, GlobalImpactWorkerMetrics metrics, ExecutorService executor, Clock clock) {
        this.control = control; this.properties = properties; this.limiter = limiter; this.metrics = metrics;
        this.executor = executor; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tpip.global-impact-worker.poll-interval:5s}")
    public void execute() {
        if (!properties.isEnabled() || !polling.compareAndSet(false, true)) return;
        try {
            List<GlobalImpactControlClient.GlobalImpactTask> tasks = control.claimRunnable();
            metrics.poll("success", tasks.size());
            tasks.stream().limit(properties.getMaxConcurrentJobs()).map(task ->
                    CompletableFuture.runAsync(() -> execute(task), executor)).toList()
                    .forEach(CompletableFuture::join);
        } catch (RuntimeException exception) {
            metrics.poll("failure", 0);
            LOG.warn("TPIP_GLOBAL_IMPACT_POLL_FAILED workerId={} reason={}", properties.getWorkerId(), safe(exception));
        } finally { polling.set(false); }
    }

    private void execute(GlobalImpactControlClient.GlobalImpactTask task) {
        if (!limiter.tryAcquire()) { metrics.rateLimited(); return; }
        if (task.expiresAt() != null && !task.expiresAt().isAfter(clock.instant().plus(properties.getExpiryWarning()))) {
            metrics.expiring();
            LOG.warn("TPIP_GLOBAL_IMPACT_JOB_EXPIRING jobId={} expiresAt={}", task.jobId(), task.expiresAt());
        }
        long started = System.nanoTime(); metrics.enter();
        try {
            var result = control.runBatch(task);
            metrics.batch("success", result.claimedCount(), elapsed(started));
            LOG.info("TPIP_GLOBAL_IMPACT_BATCH_COMPLETED jobId={} claimed={} status={}", task.jobId(),
                    result.claimedCount(), result.job().status());
        } catch (RuntimeException exception) {
            metrics.batch("failure", 0, elapsed(started));
            LOG.warn("TPIP_GLOBAL_IMPACT_BATCH_FAILED jobId={} reason={}", task.jobId(), safe(exception));
        } finally { metrics.leave(); }
    }
    private static Duration elapsed(long started) { return Duration.ofNanos(Math.max(0, System.nanoTime() - started)); }
    private static String safe(RuntimeException exception) {
        String value = exception.getMessage(); return value == null ? exception.getClass().getSimpleName() : value;
    }
}
