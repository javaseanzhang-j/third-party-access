package com.ftk.tpip.worker.health;

import java.time.Instant;

interface HealthWindowGuard {
    boolean tryAcquire(long deploymentId, Instant windowEnd);
    void complete(long deploymentId);
    void release(long deploymentId, Instant windowEnd);
}
