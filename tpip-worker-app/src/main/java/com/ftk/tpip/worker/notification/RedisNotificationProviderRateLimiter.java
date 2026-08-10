package com.ftk.tpip.worker.notification;

import java.time.Clock;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

final class RedisNotificationProviderRateLimiter implements NotificationProviderRateLimiter {
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>(
            "local count=redis.call('INCR',KEYS[1]); "
                    + "if count==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; return count;", Long.class);
    private final StringRedisTemplate redis;
    private final Clock clock;

    RedisNotificationProviderRateLimiter(StringRedisTemplate redis, Clock clock) {
        this.redis = redis; this.clock = clock;
    }

    @Override
    public void acquire(String provider, String channel, int limit) {
        long second = clock.millis() / 1000;
        String key = "tpip:notification:rate:" + provider + ":" + channel + ":" + second;
        try {
            Long count = redis.execute(ACQUIRE, List.of(key), "2000");
            if (count == null) throw new NotificationDeliveryException("PROVIDER_RATE_LIMIT_UNAVAILABLE");
            if (count > limit) throw new NotificationDeliveryException(provider + "_DISTRIBUTED_RATE_LIMITED");
        } catch (DataAccessException exception) {
            throw new NotificationDeliveryException("PROVIDER_RATE_LIMIT_UNAVAILABLE", exception);
        }
    }
}
