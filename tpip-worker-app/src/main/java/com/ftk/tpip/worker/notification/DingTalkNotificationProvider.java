package com.ftk.tpip.worker.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

final class DingTalkNotificationProvider implements NotificationProvider {
    private final VendorNotificationSupport support;
    private final NotificationSecretResolver secrets;
    private final NotificationProviderRateLimiter limiter;
    private final Clock clock;

    DingTalkNotificationProvider(HttpClient http, ObjectMapper json, NotificationDispatcherProperties properties,
            NotificationSecretResolver secrets, NotificationProviderRateLimiter limiter, Clock clock) {
        this.support = new VendorNotificationSupport(http, json, properties);
        this.secrets = secrets; this.limiter = limiter; this.clock = clock;
    }

    @Override public boolean supports(NotificationTask task) { return "DINGTALK".equals(task.providerType()); }

    @Override
    public void deliver(NotificationTask task) {
        JsonNode configuration = support.configuration(task);
        limiter.acquire("DINGTALK", task.channelCode(), support.rate(configuration, 20));
        String query = VendorNotificationSupport.query("access_token", secrets.resolve(task.authorizationSecretRef()));
        JsonNode signingRef = configuration.get("signingSecretRef");
        if (signingRef != null && signingRef.isTextual()) {
            long timestamp = clock.millis();
            query += "&timestamp=" + timestamp + "&" + VendorNotificationSupport.query("sign",
                    sign(timestamp, secrets.resolve(signingRef.asText())));
        }
        JsonNode response = support.post(task, support.endpoint(task, query));
        long error = response.path("errcode").asLong(Long.MIN_VALUE);
        if (error == 0) return;
        if (error == 310000) throw new NotificationDeliveryException("DINGTALK_SECURITY_POLICY_REJECTED");
        if (error == 40035 || error == 40014) throw new NotificationDeliveryException("DINGTALK_CREDENTIAL_REJECTED");
        if (error == 130101) throw new NotificationDeliveryException("DINGTALK_REMOTE_RATE_LIMITED");
        throw new NotificationDeliveryException("DINGTALK_REMOTE_REJECTED");
    }

    private static String sign(long timestamp, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getEncoder().encodeToString(mac.doFinal((timestamp + "\n" + secret)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new NotificationDeliveryException("DINGTALK_SIGNING_FAILURE", exception);
        }
    }
}
