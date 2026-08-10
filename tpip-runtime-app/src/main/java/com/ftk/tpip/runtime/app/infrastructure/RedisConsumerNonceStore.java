package com.ftk.tpip.runtime.app.infrastructure;
import com.ftk.tpip.runtime.access.ConsumerNonceStore;
import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
public final class RedisConsumerNonceStore implements ConsumerNonceStore {
    private final StringRedisTemplate redis;public RedisConsumerNonceStore(StringRedisTemplate redis){this.redis=redis;}
    @Override public boolean claim(String appKey,String nonce,Duration ttl){Boolean claimed=redis.opsForValue().setIfAbsent("tpip:consumer-nonce:"+appKey+":"+nonce,"1",ttl);return Boolean.TRUE.equals(claimed);}
}
