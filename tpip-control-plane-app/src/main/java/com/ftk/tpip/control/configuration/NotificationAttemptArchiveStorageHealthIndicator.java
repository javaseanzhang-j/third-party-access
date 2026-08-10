package com.ftk.tpip.control.configuration;

import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("notificationAttemptArchiveStorage")
public final class NotificationAttemptArchiveStorageHealthIndicator implements HealthIndicator {
    private final NotificationAttemptArchiveStore store;

    public NotificationAttemptArchiveStorageHealthIndicator(NotificationAttemptArchiveStore store) {
        this.store = store;
    }

    @Override public Health health() {
        var value = store.probe();
        var builder = value.reachable() && value.ready() ? Health.up() : Health.down();
        return builder.withDetail("provider", value.provider()).withDetail("reachable", value.reachable())
                .withDetail("ready", value.ready()).withDetail("versioningEnabled", value.versioningEnabled())
                .withDetail("objectLockEnabled", value.objectLockEnabled()).withDetail("message", value.detail())
                .build();
    }
}
