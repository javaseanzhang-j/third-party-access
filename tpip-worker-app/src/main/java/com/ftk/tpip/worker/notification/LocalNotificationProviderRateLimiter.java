package com.ftk.tpip.worker.notification;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

final class LocalNotificationProviderRateLimiter implements NotificationProviderRateLimiter {
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    LocalNotificationProviderRateLimiter(Clock clock) { this.clock = clock; }

    @Override
    public synchronized void acquire(String provider, String channel, int limit) {
        long second = clock.millis() / 1000;
        String key = provider + ":" + channel;
        Window current = windows.get(key);
        if (current == null || current.second() != second) current = new Window(second, 0);
        if (current.count() >= limit) throw new NotificationDeliveryException(provider + "_LOCAL_RATE_LIMITED");
        windows.put(key, new Window(second, current.count() + 1));
    }

    private record Window(long second, int count) {}
}
