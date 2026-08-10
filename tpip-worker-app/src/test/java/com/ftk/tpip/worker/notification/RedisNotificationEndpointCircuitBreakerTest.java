package com.ftk.tpip.worker.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RedisNotificationEndpointCircuitBreakerTest {
    @Test
    void onlyCountsEndpointAvailabilityFailures() {
        assertTrue(RedisNotificationEndpointCircuitBreaker.affectsCircuit("WEBHOOK_IO_FAILURE"));
        assertTrue(RedisNotificationEndpointCircuitBreaker.affectsCircuit("WEBHOOK_HTTP_503"));
        assertTrue(RedisNotificationEndpointCircuitBreaker.affectsCircuit("WECOM_RESPONSE_INVALID"));
        assertFalse(RedisNotificationEndpointCircuitBreaker.affectsCircuit("WEBHOOK_HTTP_429"));
        assertFalse(RedisNotificationEndpointCircuitBreaker.affectsCircuit("WECOM_CREDENTIAL_REJECTED"));
        assertFalse(RedisNotificationEndpointCircuitBreaker.affectsCircuit("PROVIDER_RATE_LIMIT_UNAVAILABLE"));
        assertTrue(RedisNotificationEndpointCircuitBreaker.provesEndpointReachable("WEBHOOK_HTTP_429"));
        assertTrue(RedisNotificationEndpointCircuitBreaker.provesEndpointReachable("WECOM_CREDENTIAL_REJECTED"));
        assertFalse(RedisNotificationEndpointCircuitBreaker.provesEndpointReachable("PROVIDER_CONFIGURATION_INVALID"));
        assertFalse(RedisNotificationEndpointCircuitBreaker.provesEndpointReachable("WEBHOOK_IO_FAILURE"));
    }
}
