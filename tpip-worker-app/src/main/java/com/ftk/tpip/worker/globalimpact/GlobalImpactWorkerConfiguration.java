package com.ftk.tpip.worker.globalimpact;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GlobalImpactWorkerProperties.class)
class GlobalImpactWorkerConfiguration {
    @Bean GlobalImpactControlClient globalImpactControlClient(HttpClient http, ObjectMapper json,
            GlobalImpactWorkerProperties properties) {
        properties.validate();
        return new HttpGlobalImpactControlClient(http, json, properties);
    }
    @Bean LocalBatchRateLimiter globalImpactBatchRateLimiter(GlobalImpactWorkerProperties properties) {
        return new LocalBatchRateLimiter(properties.getMaxBatchesPerMinute(), Clock.systemUTC());
    }
    @Bean(destroyMethod = "shutdown") ExecutorService globalImpactExecutor(GlobalImpactWorkerProperties properties) {
        return Executors.newFixedThreadPool(properties.getMaxConcurrentJobs(),
                Thread.ofVirtual().name("global-impact-", 0).factory());
    }
}
