package com.ftk.tpip.worker.globalimpact;

import java.time.Clock;
import java.time.Instant;

final class LocalBatchRateLimiter {
    private final int limit;
    private final Clock clock;
    private Instant window;
    private int used;
    LocalBatchRateLimiter(int limit, Clock clock) { this.limit = limit; this.clock = clock; this.window = clock.instant(); }
    synchronized boolean tryAcquire() {
        Instant now = clock.instant();
        if (!now.isBefore(window.plusSeconds(60))) { window = now; used = 0; }
        if (used >= limit) return false;
        used++; return true;
    }
}
