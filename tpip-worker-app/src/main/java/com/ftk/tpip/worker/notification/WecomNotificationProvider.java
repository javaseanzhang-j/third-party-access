package com.ftk.tpip.worker.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;

final class WecomNotificationProvider implements NotificationProvider {
    private final VendorNotificationSupport support;
    private final NotificationSecretResolver secrets;
    private final NotificationProviderRateLimiter limiter;

    WecomNotificationProvider(HttpClient http, ObjectMapper json, NotificationDispatcherProperties properties,
            NotificationSecretResolver secrets, NotificationProviderRateLimiter limiter) {
        this.support = new VendorNotificationSupport(http, json, properties);
        this.secrets = secrets; this.limiter = limiter;
    }

    @Override public boolean supports(NotificationTask task) { return "WECOM".equals(task.providerType()); }

    @Override
    public void deliver(NotificationTask task) {
        JsonNode configuration = support.configuration(task);
        limiter.acquire("WECOM", task.channelCode(), support.rate(configuration, 20));
        String key = secrets.resolve(task.authorizationSecretRef());
        JsonNode response = support.post(task, support.endpoint(task, VendorNotificationSupport.query("key", key)));
        long error = response.path("errcode").asLong(Long.MIN_VALUE);
        if (error == 0) return;
        if (error == 40014 || error == 93004) throw new NotificationDeliveryException("WECOM_CREDENTIAL_REJECTED");
        if (error == 45009) throw new NotificationDeliveryException("WECOM_REMOTE_RATE_LIMITED");
        throw new NotificationDeliveryException("WECOM_REMOTE_REJECTED");
    }
}
