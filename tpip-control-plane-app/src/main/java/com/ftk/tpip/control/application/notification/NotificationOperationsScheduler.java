package com.ftk.tpip.control.application.notification;

import com.ftk.tpip.control.configuration.NotificationDeliveryProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationOperationsScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(NotificationOperationsScheduler.class);
    private static final String ACTOR = "system-notification-operations";
    private final NotificationOperationsAutomationService service;
    private final NotificationDeliveryProperties properties;
    private final Clock clock;

    @Autowired
    public NotificationOperationsScheduler(NotificationOperationsAutomationService service,
            NotificationDeliveryProperties properties) {
        this(service, properties, Clock.systemUTC());
    }

    NotificationOperationsScheduler(NotificationOperationsAutomationService service,
            NotificationDeliveryProperties properties, Clock clock) {
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${tpip.notification-delivery.operations-automation-poll-interval:1m}")
    public void evaluateCompletedWindow() {
        if (!properties.isOperationsAutomationEnabled()) return;
        Instant end = completedWindowEnd(clock.instant(), properties.getOperationsEvaluationWindow(),
                properties.getOperationsEvaluationDelay());
        Instant start = end.minus(properties.getOperationsEvaluationWindow());
        for (String environment : properties.getOperationsEnvironments()) {
            try {
                service.evaluate(environment.trim(), start, end, ACTOR);
                service.escalateDueAlerts(environment.trim());
                service.repeatDueAlerts(environment.trim());
            } catch (RuntimeException exception) {
                LOG.error("Notification operations evaluation failed for environment {}: {}",
                        environment, exception.getClass().getSimpleName());
            }
        }
    }

    static Instant completedWindowEnd(Instant now, Duration window, Duration delay) {
        long windowMillis = window.toMillis();
        long effectiveMillis = now.minus(delay).toEpochMilli();
        return Instant.ofEpochMilli(Math.floorDiv(effectiveMillis, windowMillis) * windowMillis);
    }
}
