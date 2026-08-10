package com.ftk.tpip.worker.notification;

interface NotificationProviderRateLimiter {
    void acquire(String provider, String channel, int limit);
}
