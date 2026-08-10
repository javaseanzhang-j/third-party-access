package com.ftk.tpip.worker.notification;

import java.util.List;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public final class NotificationDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationDispatcher.class);
    private final ControlPlaneNotificationClient control;
    private final List<NotificationProvider> providers;
    private final NotificationDispatcherProperties properties;
    private final NotificationDispatcherMetrics metrics;
    private final NotificationEndpointCircuitBreaker circuitBreaker;

    NotificationDispatcher(ControlPlaneNotificationClient control, List<NotificationProvider> providers,
            NotificationDispatcherProperties properties, NotificationDispatcherMetrics metrics) {
        this(control, providers, properties, metrics, new NotificationEndpointCircuitBreaker() {
            public void beforeDelivery(NotificationTask task) {}
            public void recordSuccess(NotificationTask task) {}
            public void recordFailure(NotificationTask task, String errorCode) {}
        });
    }

    @org.springframework.beans.factory.annotation.Autowired
    NotificationDispatcher(ControlPlaneNotificationClient control, List<NotificationProvider> providers,
            NotificationDispatcherProperties properties, NotificationDispatcherMetrics metrics,
            NotificationEndpointCircuitBreaker circuitBreaker) {
        this.control = control;
        this.providers = List.copyOf(providers);
        this.properties = properties;
        this.metrics = metrics;
        this.circuitBreaker = circuitBreaker;
    }

    @Scheduled(fixedDelayString = "${tpip.notification-dispatcher.poll-interval:2s}")
    public void dispatch() {
        if (!properties.isEnabled()) return;
        List<NotificationTask> tasks;
        try {
            tasks = control.claim();
            metrics.claimSucceeded(tasks.size());
        } catch (NotificationDeliveryException exception) {
            metrics.claimFailed(exception.errorCode());
            LOG.warn("TPIP_NOTIFICATION_CLAIM_FAILED workerId={} reason={}",
                    properties.getWorkerId(), exception.errorCode());
            return;
        }
        for (NotificationTask task : tasks) dispatch(task);
    }

    private void dispatch(NotificationTask task) {
        long started = System.nanoTime();
        try {
            circuitBreaker.beforeDelivery(task);
            boolean handled = false;
            for (NotificationProvider provider : providers) {
                if (!provider.supports(task)) continue;
                provider.deliver(task);
                handled = true;
            }
            if (!handled) throw new NotificationDeliveryException("NO_NOTIFICATION_PROVIDER");
            circuitBreaker.recordSuccess(task);
            control.delivered(task.id());
            metrics.delivery(task.channelCode(), "success", "DELIVERED", elapsed(started));
            LOG.info("TPIP_NOTIFICATION_DELIVERED eventId={} deliveryId={} channel={} eventType={} attempt={}",
                    task.eventId(), task.id(), task.channelCode(), task.eventType(), task.attemptCount());
        } catch (NotificationDeliveryException exception) {
            circuitBreaker.recordFailure(task, exception.errorCode());
            reportFailure(task, exception.errorCode(), exception.retryAfter(), started);
        } catch (RuntimeException exception) {
            reportFailure(task, "NOTIFICATION_PROVIDER_FAILURE", null, started);
        }
    }

    private void reportFailure(NotificationTask task, String errorCode, Duration retryAfter, long started) {
        try {
            control.failed(task.id(), errorCode, retryAfter);
        } catch (NotificationDeliveryException reportFailure) {
            LOG.warn("TPIP_NOTIFICATION_FAILURE_REPORT_FAILED eventId={} deliveryId={} channel={} reason={}",
                    task.eventId(), task.id(), task.channelCode(), reportFailure.errorCode());
            return;
        }
        metrics.delivery(task.channelCode(), "failure", errorCode, elapsed(started));
        LOG.warn("TPIP_NOTIFICATION_DELIVERY_FAILED eventId={} deliveryId={} channel={} eventType={} attempt={} reason={}",
                task.eventId(), task.id(), task.channelCode(), task.eventType(), task.attemptCount(), errorCode);
    }

    private static Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0, System.nanoTime() - started));
    }
}
