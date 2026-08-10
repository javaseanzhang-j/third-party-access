package com.ftk.tpip.worker.globalimpact;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import org.junit.jupiter.api.Test;

class LocalBatchRateLimiterTest {
    @Test void enforcesAndResetsTheMinuteWindow() {
        var clock = new MutableClock(Instant.parse("2026-08-09T00:00:00Z"));
        var limiter = new LocalBatchRateLimiter(2, clock);
        assertTrue(limiter.tryAcquire()); assertTrue(limiter.tryAcquire()); assertFalse(limiter.tryAcquire());
        clock.now = clock.now.plusSeconds(60);
        assertTrue(limiter.tryAcquire());
    }
    private static final class MutableClock extends Clock {
        private Instant now; MutableClock(Instant now) { this.now = now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
