package com.ftk.tpip.control.application.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.release.domain.model.NotificationProviderCapability;
import com.ftk.tpip.release.domain.model.NotificationProviderType;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class NotificationProviderGovernance {
    private static final Set<String> WECOM_TYPES = Set.of("text", "markdown");
    private static final Set<String> DINGTALK_TYPES = Set.of("text", "markdown");

    public List<NotificationProviderCapability> capabilities() {
        return List.of(
                new NotificationProviderCapability(NotificationProviderType.WEBHOOK, "1.0", "application/json",
                        List.of("json"), "AUTHORIZATION_HEADER", false, 100),
                new NotificationProviderCapability(NotificationProviderType.WECOM, "1.0", "application/json",
                        WECOM_TYPES.stream().sorted().toList(), "QUERY_KEY", false, 20),
                new NotificationProviderCapability(NotificationProviderType.DINGTALK, "1.0", "application/json",
                        DINGTALK_TYPES.stream().sorted().toList(), "QUERY_ACCESS_TOKEN", true, 20));
    }

    public void validateChannel(NotificationProviderType provider, JsonNode configuration, String credentialRef) {
        if (provider == null) throw new IllegalArgumentException("providerType is required");
        if (configuration == null || !configuration.isObject()) {
            throw new IllegalArgumentException("configuration must be a JSON object");
        }
        if (provider != NotificationProviderType.WEBHOOK && (credentialRef == null || credentialRef.isBlank())) {
            throw new IllegalArgumentException(provider + " requires authorizationSecretRef");
        }
        Set<String> allowed = provider == NotificationProviderType.DINGTALK
                ? Set.of("rateLimitPerSecond", "signingSecretRef")
                : provider == NotificationProviderType.WECOM ? Set.of("rateLimitPerSecond") : null;
        if (allowed == null) return;
        configuration.fieldNames().forEachRemaining(field -> {
            if (!allowed.contains(field)) throw new IllegalArgumentException("unsupported provider configuration: " + field);
        });
        JsonNode rate = configuration.get("rateLimitPerSecond");
        if (rate != null && (!rate.isIntegralNumber() || rate.asInt() < 1 || rate.asInt() > 100)) {
            throw new IllegalArgumentException("rateLimitPerSecond must be between 1 and 100");
        }
        JsonNode signing = configuration.get("signingSecretRef");
        if (signing != null && (!signing.isTextual() || !allowedSecretRef(signing.asText()))) {
            throw new IllegalArgumentException("signingSecretRef must use an allowed env reference");
        }
    }

    public void validateTemplate(NotificationProviderType provider, JsonNode document, String contentType) {
        if (!"application/json".equals(contentType)) throw new IllegalArgumentException("contentType must be application/json");
        if (document == null || !document.isObject()) throw new IllegalArgumentException("templateDocument must be a JSON object");
        if (provider == NotificationProviderType.WEBHOOK) return;
        String type = document.path("msgtype").asText();
        Set<String> supported = provider == NotificationProviderType.WECOM ? WECOM_TYPES : DINGTALK_TYPES;
        if (!supported.contains(type)) throw new IllegalArgumentException(provider + " msgtype must be text or markdown");
        JsonNode body = document.get(type);
        if (body == null || !body.isObject()) throw new IllegalArgumentException(provider + " message body is missing");
        if (type.equals("text") && !body.path("content").isTextual()) {
            throw new IllegalArgumentException(provider + " text.content must be a string");
        }
        if (type.equals("markdown")) {
            if (provider == NotificationProviderType.WECOM && !body.path("content").isTextual()) {
                throw new IllegalArgumentException("WECOM markdown.content must be a string");
            }
            if (provider == NotificationProviderType.DINGTALK
                    && (!body.path("title").isTextual() || !body.path("text").isTextual())) {
                throw new IllegalArgumentException("DINGTALK markdown.title and markdown.text must be strings");
            }
        }
    }

    private static boolean allowedSecretRef(String value) {
        return value != null && value.matches("env://TPIP_SECRET_[A-Z0-9_]{1,200}");
    }
}
