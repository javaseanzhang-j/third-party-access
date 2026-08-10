package com.ftk.tpip.worker.notification;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

final class RedisNotificationEndpointCircuitBreaker implements NotificationEndpointCircuitBreaker {
    private static final Logger LOG = LoggerFactory.getLogger(RedisNotificationEndpointCircuitBreaker.class);
    private static final DefaultRedisScript<Long> RECORD_FAILURE = new DefaultRedisScript<>(
            "if redis.call('EXISTS',KEYS[3])==1 then redis.call('DEL',KEYS[3]); "
                    + "redis.call('SET',KEYS[2],'1','PX',ARGV[3]); "
                    + "redis.call('SET',KEYS[4],'1','PX',ARGV[4]); redis.call('DEL',KEYS[1]); return 2; end; "
                    + "local count=redis.call('INCR',KEYS[1]); "
                    + "if count==1 then redis.call('PEXPIRE',KEYS[1],ARGV[1]); end; "
                    + "if count>=tonumber(ARGV[2]) then redis.call('SET',KEYS[2],'1','PX',ARGV[3]); "
                    + "redis.call('SET',KEYS[4],'1','PX',ARGV[4]); "
                    + "redis.call('DEL',KEYS[1]); return 1; end; return 0;", Long.class);
    private static final DefaultRedisScript<Long> ACQUIRE = new DefaultRedisScript<>(
            "if redis.call('EXISTS',KEYS[1])==1 then return redis.call('PTTL',KEYS[1]); end; "
                    + "if redis.call('EXISTS',KEYS[2])==1 then "
                    + "local permit=redis.call('SET',KEYS[3],'1','NX','PX',ARGV[1]); "
                    + "if permit then return 0; end; local ttl=redis.call('PTTL',KEYS[3]); "
                    + "if ttl<1 then return tonumber(ARGV[1]); end; return ttl; end; return -1;", Long.class);
    private final StringRedisTemplate redis;
    private final NotificationDispatcherProperties properties;

    RedisNotificationEndpointCircuitBreaker(StringRedisTemplate redis, NotificationDispatcherProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public void beforeDelivery(NotificationTask task) {
        try {
            Long result = redis.execute(ACQUIRE,
                    List.of(openKey(task), recoveryKey(task), probeKey(task)),
                    Long.toString(properties.getCircuitHalfOpenProbeLease().toMillis()));
            if (result == null || result <= 0) return;
            throw new NotificationDeliveryException("ENDPOINT_CIRCUIT_OPEN", Duration.ofMillis(result));
        } catch (NotificationDeliveryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOG.warn("TPIP_NOTIFICATION_CIRCUIT_CHECK_UNAVAILABLE asset={}", assetKey(task));
        }
    }

    @Override
    public void recordSuccess(NotificationTask task) {
        try { redis.delete(List.of(failureKey(task), openKey(task), recoveryKey(task), probeKey(task))); }
        catch (RuntimeException exception) {
            LOG.warn("TPIP_NOTIFICATION_CIRCUIT_RESET_UNAVAILABLE asset={}", assetKey(task));
        }
    }

    @Override
    public void recordFailure(NotificationTask task, String errorCode) {
        if (provesEndpointReachable(errorCode)) {
            recordSuccess(task);
            return;
        }
        if (!affectsCircuit(errorCode)) return;
        try {
            long recoveryMillis = properties.getCircuitOpenDuration().plus(
                    properties.getCircuitFailureWindow()).toMillis();
            redis.execute(RECORD_FAILURE,
                    List.of(failureKey(task), openKey(task), probeKey(task), recoveryKey(task)),
                    Long.toString(properties.getCircuitFailureWindow().toMillis()),
                    Integer.toString(properties.getCircuitFailureThreshold()),
                    Long.toString(properties.getCircuitOpenDuration().toMillis()),
                    Long.toString(recoveryMillis));
        } catch (RuntimeException exception) {
            LOG.warn("TPIP_NOTIFICATION_CIRCUIT_RECORD_UNAVAILABLE asset={}", assetKey(task));
        }
    }

    static boolean affectsCircuit(String errorCode) {
        String code = errorCode == null ? "" : errorCode.toUpperCase(java.util.Locale.ROOT);
        if (code.contains("_IO_FAILURE") || code.contains("_RESPONSE_INVALID")) return true;
        int marker = code.lastIndexOf("_HTTP_");
        if (marker < 0) return false;
        try {
            int status = Integer.parseInt(code.substring(marker + 6));
            return status == 408 || status == 425 || status >= 500;
        } catch (NumberFormatException ignored) { return false; }
    }

    static boolean provesEndpointReachable(String errorCode) {
        String code = errorCode == null ? "" : errorCode.toUpperCase(java.util.Locale.ROOT);
        if (code.contains("REMOTE_RATE_LIMITED") || code.contains("REMOTE_REJECTED")
                || code.contains("CREDENTIAL_REJECTED") || code.contains("SECURITY_POLICY_REJECTED")) {
            return true;
        }
        int marker = code.lastIndexOf("_HTTP_");
        if (marker < 0) return false;
        try {
            int status = Integer.parseInt(code.substring(marker + 6));
            return status >= 400 && status < 500 && status != 408 && status != 425;
        } catch (NumberFormatException ignored) { return false; }
    }

    private static String failureKey(NotificationTask task) {
        return "tpip:notification:circuit:failures:" + assetKey(task);
    }
    private static String openKey(NotificationTask task) {
        return "tpip:notification:circuit:open:" + assetKey(task);
    }
    private static String recoveryKey(NotificationTask task) {
        return "tpip:notification:circuit:recovery:" + assetKey(task);
    }
    private static String probeKey(NotificationTask task) {
        return "tpip:notification:circuit:probe:" + assetKey(task);
    }
    private static String assetKey(NotificationTask task) {
        return task.endpointRevisionId() != null ? "endpoint-" + task.endpointRevisionId()
                : "channel-version-" + task.channelVersionId();
    }
}
