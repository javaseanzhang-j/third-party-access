package com.ftk.tpip.worker.health;

import java.time.Instant;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
final class RedisHealthWindowGuard implements HealthWindowGuard {
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end",
            Long.class);
    private final StringRedisTemplate redis;
    private final HealthWorkerProperties properties;

    RedisHealthWindowGuard(StringRedisTemplate redis, HealthWorkerProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(long deploymentId, Instant windowEnd) {
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey(deploymentId)))) return false;
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(lockKey(deploymentId, windowEnd),
                properties.getWorkerId(), properties.getLockTtl()));
    }

    @Override
    public void complete(long deploymentId) {
        if (!properties.getCooldown().isZero()) {
            redis.opsForValue().set(cooldownKey(deploymentId), properties.getWorkerId(), properties.getCooldown());
        }
    }

    @Override
    public void release(long deploymentId, Instant windowEnd) {
        redis.execute(RELEASE, List.of(lockKey(deploymentId, windowEnd)), properties.getWorkerId());
    }

    private static String lockKey(long deploymentId, Instant end) {
        return "tpip:health-worker:window:" + deploymentId + ":" + end.getEpochSecond();
    }

    private static String cooldownKey(long deploymentId) {
        return "tpip:health-worker:cooldown:" + deploymentId;
    }
}
