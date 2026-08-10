package com.ftk.tpip.worker.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(NotificationDispatcherProperties.class)
public class NotificationDispatcherConfiguration {
    @Bean
    ControlPlaneNotificationClient controlPlaneNotificationClient(HttpClient http, ObjectMapper json,
            NotificationDispatcherProperties properties) {
        properties.validate();
        return new ControlPlaneNotificationClient(http, json, properties);
    }

    @Bean
    WebhookNotificationProvider webhookNotificationProvider(HttpClient http, ObjectMapper json,
            NotificationDispatcherProperties properties, NotificationSecretResolver secrets) {
        return new WebhookNotificationProvider(http, json, properties, secrets);
    }

    @Bean
    NotificationProviderRateLimiter notificationProviderRateLimiter(StringRedisTemplate redis) {
        return new RedisNotificationProviderRateLimiter(redis, Clock.systemUTC());
    }

    @Bean
    NotificationEndpointCircuitBreaker notificationEndpointCircuitBreaker(StringRedisTemplate redis,
            NotificationDispatcherProperties properties) {
        return new RedisNotificationEndpointCircuitBreaker(redis, properties);
    }

    @Bean
    WecomNotificationProvider wecomNotificationProvider(HttpClient http, ObjectMapper json,
            NotificationDispatcherProperties properties, NotificationSecretResolver secrets,
            NotificationProviderRateLimiter limiter) {
        return new WecomNotificationProvider(http, json, properties, secrets, limiter);
    }

    @Bean
    DingTalkNotificationProvider dingTalkNotificationProvider(HttpClient http, ObjectMapper json,
            NotificationDispatcherProperties properties, NotificationSecretResolver secrets,
            NotificationProviderRateLimiter limiter) {
        return new DingTalkNotificationProvider(http, json, properties, secrets, limiter, Clock.systemUTC());
    }

    @Bean
    NotificationSecretResolver notificationSecretResolver() {
        return new EnvironmentNotificationSecretResolver();
    }
}
