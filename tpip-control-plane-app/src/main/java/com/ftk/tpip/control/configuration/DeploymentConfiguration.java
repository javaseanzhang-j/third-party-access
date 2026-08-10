package com.ftk.tpip.control.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.control.application.deployment.RuntimePreheatClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DeploymentRuntimeProperties.class, NotificationDeliveryProperties.class,
        NotificationAttemptArchiveProperties.class, WorkspaceVerificationProperties.class,
        WorkspaceRemoteCallProperties.class, RegressionSchedulerProperties.class,
        DriftGovernanceReminderPreviewProperties.class, GlobalImpactJobMaintenanceProperties.class,
        GlobalImpactSchedulingProperties.class})
public class DeploymentConfiguration {
    @Bean
    RuntimePreheatClient runtimePreheatClient(DeploymentRuntimeProperties properties, ObjectMapper json) {
        return new RuntimePreheatClient(properties.getRuntimeTargets(),
                properties.getMinimumSuccessfulInstances(), properties.getConnectTimeout(),
                properties.getReadTimeout(), json);
    }
}
