package com.ftk.tpip.worker.health;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(HealthWorkerProperties.class)
public class HealthWorkerConfiguration {
    @Bean
    Clock healthWorkerClock() { return Clock.systemUTC(); }

    @Bean
    HttpClient healthWorkerHttpClient(HealthWorkerProperties properties) {
        properties.validate();
        return HttpClient.newBuilder().connectTimeout(properties.getConnectTimeout()).build();
    }

    @Bean
    ControlPlaneHealthClient controlPlaneHealthClient(HttpClient http, ObjectMapper json,
            HealthWorkerProperties properties) {
        return new ControlPlaneHealthClient(http, json, properties);
    }

    @Bean
    PrometheusHealthEvidenceClient prometheusHealthEvidenceClient(HttpClient http, ObjectMapper json,
            HealthWorkerProperties properties, Clock clock) {
        return new PrometheusHealthEvidenceClient(http, json, properties, clock);
    }
}
