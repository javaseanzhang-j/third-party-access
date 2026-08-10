package com.ftk.tpip.runtime.access;
import java.time.Duration;
public interface ConsumerNonceStore { boolean claim(String appKey,String nonce,Duration ttl); }
