package com.ftk.tpip.control.application.notification;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class NotificationCircuitOperationsService {
    public enum AssetType { ENDPOINT_REVISION, CHANNEL_VERSION }
    private final StringRedisTemplate redis;

    public NotificationCircuitOperationsService(StringRedisTemplate redis) { this.redis = redis; }

    public NotificationCircuitStatus status(AssetType assetType, long assetId) {
        if (assetType == null || assetId <= 0) throw new IllegalArgumentException("circuit asset is invalid");
        String asset = assetType == AssetType.ENDPOINT_REVISION
                ? "endpoint-" + assetId : "channel-version-" + assetId;
        String prefix = "tpip:notification:circuit:";
        try {
            Boolean open = redis.hasKey(prefix + "open:" + asset);
            Boolean recovery = redis.hasKey(prefix + "recovery:" + asset);
            Boolean probe = redis.hasKey(prefix + "probe:" + asset);
            String failures = redis.opsForValue().get(prefix + "failures:" + asset);
            String state = Boolean.TRUE.equals(open) ? "OPEN"
                    : Boolean.TRUE.equals(recovery) && Boolean.TRUE.equals(probe) ? "HALF_OPEN_IN_FLIGHT"
                    : Boolean.TRUE.equals(recovery) ? "HALF_OPEN_READY" : "CLOSED";
            return new NotificationCircuitStatus(assetType.name(), assetId, state, number(failures),
                    ttl(open, prefix + "open:" + asset), ttl(probe, prefix + "probe:" + asset));
        } catch (RuntimeException exception) {
            return new NotificationCircuitStatus(assetType.name(), assetId, "UNAVAILABLE", 0, null, null);
        }
    }

    private Long ttl(Boolean exists, String key) {
        if (!Boolean.TRUE.equals(exists)) return null;
        Long value = redis.getExpire(key, TimeUnit.MILLISECONDS);
        return value == null || value < 0 ? null : value;
    }

    private static long number(String value) {
        if (value == null) return 0;
        try { return Math.max(0, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }
}
