package com.ftk.tpip.control.configuration;

import com.ftk.tpip.adapters.persistence.jdbc.provider.JdbcCredentialRefRepository;
import com.ftk.tpip.adapters.persistence.jdbc.access.JdbcAccessChannelRepository;
import com.ftk.tpip.adapters.persistence.jdbc.catalog.JdbcCanonicalOperationRepository;
import com.ftk.tpip.adapters.persistence.jdbc.catalog.JdbcCatalogHierarchyRepository;
import com.ftk.tpip.adapters.persistence.jdbc.catalog.JdbcCanonicalContractRepository;
import com.ftk.tpip.adapters.persistence.jdbc.integration.JdbcIntegrationBindingRepository;
import com.ftk.tpip.adapters.persistence.jdbc.integration.JdbcIntegrationBindingVersionRepository;
import com.ftk.tpip.adapters.persistence.jdbc.integration.JdbcIntegrationMappingRepository;
import com.ftk.tpip.adapters.persistence.jdbc.integration.JdbcPolicyTypeRepository;
import com.ftk.tpip.adapters.persistence.jdbc.integration.JdbcIntegrationPolicyRepository;
import com.ftk.tpip.adapters.persistence.jdbc.routing.JdbcServiceRouteRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcReleaseRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcFixtureSuiteRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcVerificationBaselineRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcVerificationDriftWorkbenchRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcVerificationDriftBulkOperationRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcVerificationDriftOperationsMetricsRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcVerificationDriftPolicyImpactRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcGlobalDriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcGlobalDriftPolicyImpactJobRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcGlobalImpactJobQueryRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDriftGovernancePolicyRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDriftGovernancePolicyAssetQueryRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcWorkspaceAssetQueryRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDriftGovernanceExecutionRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDriftGovernanceReminderBatchRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDriftGovernanceReminderObservabilityRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcRegressionPolicyRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDeploymentRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDeploymentHealthEvaluationRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcDeploymentHealthAlertRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcNotificationOutboxRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcNotificationRoutingAssetRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcNotificationTemplateRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcNotificationOperationsRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcNotificationOperationsGovernanceRepository;
import com.ftk.tpip.adapters.persistence.jdbc.release.JdbcNotificationAttemptArchiveRepository;
import com.ftk.tpip.adapters.archive.FileSystemNotificationAttemptArchiveStore;
import com.ftk.tpip.adapters.archive.S3NotificationAttemptArchiveStore;
import com.ftk.tpip.adapters.notification.DefaultNotificationTemplateEngine;
import com.ftk.tpip.adapters.persistence.jdbc.provider.JdbcProviderRepository;
import com.ftk.tpip.adapters.persistence.jdbc.provider.JdbcProviderContractRepository;
import com.ftk.tpip.adapters.persistence.jdbc.provider.JdbcProviderEndpointRepository;
import com.ftk.tpip.adapters.persistence.jdbc.provider.JdbcEndpointProbeRepository;
import com.ftk.tpip.provider.domain.repository.CredentialRefRepository;
import com.ftk.tpip.access.domain.repository.AccessChannelRepository;
import com.ftk.tpip.catalog.domain.repository.CanonicalOperationRepository;
import com.ftk.tpip.catalog.domain.repository.CatalogHierarchyRepository;
import com.ftk.tpip.catalog.domain.repository.CanonicalContractRepository;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingRepository;
import com.ftk.tpip.integration.domain.repository.IntegrationBindingVersionRepository;
import com.ftk.tpip.integration.domain.repository.IntegrationMappingRepository;
import com.ftk.tpip.integration.domain.repository.PolicyTypeRepository;
import com.ftk.tpip.integration.domain.repository.IntegrationPolicyRepository;
import com.ftk.tpip.routing.domain.repository.ServiceRouteRepository;
import com.ftk.tpip.release.domain.repository.ReleaseRepository;
import com.ftk.tpip.release.domain.repository.FixtureSuiteRepository;
import com.ftk.tpip.release.domain.repository.VerificationBaselineRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftWorkbenchRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftBulkOperationRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftOperationsMetricsRepository;
import com.ftk.tpip.release.domain.repository.VerificationDriftPolicyImpactRepository;
import com.ftk.tpip.release.domain.repository.DriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactSnapshotRepository;
import com.ftk.tpip.release.domain.repository.GlobalDriftPolicyImpactJobRepository;
import com.ftk.tpip.release.domain.repository.GlobalImpactJobQueryRepository;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyRepository;
import com.ftk.tpip.release.domain.repository.DriftGovernancePolicyAssetQueryRepository;
import com.ftk.tpip.release.domain.repository.WorkspaceAssetQueryRepository;
import com.ftk.tpip.release.domain.repository.DriftGovernanceExecutionRepository;
import com.ftk.tpip.release.domain.repository.DriftGovernanceReminderBatchRepository;
import com.ftk.tpip.release.domain.repository.DriftGovernanceReminderObservabilityRepository;
import com.ftk.tpip.release.domain.repository.RegressionPolicyRepository;
import com.ftk.tpip.release.domain.repository.DeploymentRepository;
import com.ftk.tpip.release.domain.repository.DeploymentHealthEvaluationRepository;
import com.ftk.tpip.release.domain.repository.DeploymentHealthAlertRepository;
import com.ftk.tpip.release.domain.repository.NotificationOutboxRepository;
import com.ftk.tpip.release.domain.repository.NotificationRoutingAssetRepository;
import com.ftk.tpip.release.domain.repository.NotificationTemplateRepository;
import com.ftk.tpip.release.domain.repository.NotificationOperationsRepository;
import com.ftk.tpip.release.domain.repository.NotificationOperationsGovernanceRepository;
import com.ftk.tpip.release.domain.repository.NotificationAttemptArchiveRepository;
import com.ftk.tpip.release.domain.service.NotificationAttemptArchiveStore;
import com.ftk.tpip.release.domain.service.NotificationTemplateEngine;
import com.ftk.tpip.mapping.api.MappingCompiler;
import com.ftk.tpip.mapping.compiler.DefaultMappingCompiler;
import com.ftk.tpip.mapping.api.MappingEngine;
import com.ftk.tpip.mapping.execution.DefaultMappingEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.policy.api.PolicyCompiler;
import com.ftk.tpip.policy.compiler.DefaultPolicyCompiler;
import com.ftk.tpip.bundle.BundleCompiler;
import com.ftk.tpip.bundle.DefaultBundleCompiler;
import com.ftk.tpip.provider.domain.repository.ProviderContractRepository;
import com.ftk.tpip.provider.domain.repository.ProviderEndpointRepository;
import com.ftk.tpip.provider.domain.repository.EndpointProbeRepository;
import com.ftk.tpip.provider.domain.repository.ProviderRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    @Bean
    AccessChannelRepository accessChannelRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcAccessChannelRepository(jdbcTemplate);
    }

    @Bean
    CanonicalContractRepository canonicalContractRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcCanonicalContractRepository(jdbcTemplate);
    }

    @Bean
    CatalogHierarchyRepository catalogHierarchyRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcCatalogHierarchyRepository(jdbcTemplate);
    }

    @Bean
    CanonicalOperationRepository canonicalOperationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcCanonicalOperationRepository(jdbcTemplate);
    }

    @Bean
    IntegrationBindingRepository integrationBindingRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcIntegrationBindingRepository(jdbcTemplate);
    }

    @Bean
    IntegrationBindingVersionRepository integrationBindingVersionRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcIntegrationBindingVersionRepository(jdbcTemplate);
    }

    @Bean
    IntegrationMappingRepository integrationMappingRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcIntegrationMappingRepository(jdbcTemplate);
    }

    @Bean
    MappingCompiler mappingCompiler() {
        return new DefaultMappingCompiler();
    }

    @Bean
    MappingEngine mappingEngine(ObjectMapper objectMapper) {
        return new DefaultMappingEngine(objectMapper);
    }

    @Bean
    PolicyTypeRepository policyTypeRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcPolicyTypeRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    IntegrationPolicyRepository integrationPolicyRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcIntegrationPolicyRepository(jdbcTemplate);
    }

    @Bean
    ServiceRouteRepository serviceRouteRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcServiceRouteRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    PolicyCompiler policyCompiler(ObjectMapper objectMapper) {
        return new DefaultPolicyCompiler(objectMapper);
    }

    @Bean
    BundleCompiler bundleCompiler(ObjectMapper objectMapper) {
        return new DefaultBundleCompiler(objectMapper);
    }

    @Bean
    ReleaseRepository releaseRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcReleaseRepository(jdbcTemplate);
    }

    @Bean
    FixtureSuiteRepository fixtureSuiteRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcFixtureSuiteRepository(jdbcTemplate);
    }

    @Bean
    VerificationBaselineRepository verificationBaselineRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcVerificationBaselineRepository(jdbcTemplate);
    }

    @Bean
    VerificationDriftBulkOperationRepository verificationDriftBulkOperationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcVerificationDriftBulkOperationRepository(jdbcTemplate);
    }

    @Bean
    VerificationDriftWorkbenchRepository verificationDriftWorkbenchRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcVerificationDriftWorkbenchRepository(jdbcTemplate);
    }

    @Bean
    VerificationDriftOperationsMetricsRepository verificationDriftOperationsMetricsRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcVerificationDriftOperationsMetricsRepository(jdbcTemplate);
    }

    @Bean
    VerificationDriftPolicyImpactRepository verificationDriftPolicyImpactRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcVerificationDriftPolicyImpactRepository(jdbcTemplate);
    }

    @Bean
    DriftPolicyImpactSnapshotRepository driftPolicyImpactSnapshotRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDriftPolicyImpactSnapshotRepository(jdbcTemplate);
    }

    @Bean
    GlobalDriftPolicyImpactSnapshotRepository globalDriftPolicyImpactSnapshotRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcGlobalDriftPolicyImpactSnapshotRepository(jdbcTemplate);
    }

    @Bean
    GlobalDriftPolicyImpactJobRepository globalDriftPolicyImpactJobRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcGlobalDriftPolicyImpactJobRepository(jdbcTemplate);
    }

    @Bean
    GlobalImpactJobQueryRepository globalImpactJobQueryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcGlobalImpactJobQueryRepository(jdbcTemplate);
    }

    @Bean
    DriftGovernancePolicyRepository driftGovernancePolicyRepository(JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper) {
        return new JdbcDriftGovernancePolicyRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    DriftGovernancePolicyAssetQueryRepository driftGovernancePolicyAssetQueryRepository(
            JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        return new JdbcDriftGovernancePolicyAssetQueryRepository(jdbcTemplate, objectMapper);
    }

    @Bean
    WorkspaceAssetQueryRepository workspaceAssetQueryRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcWorkspaceAssetQueryRepository(jdbcTemplate);
    }

    @Bean
    DriftGovernanceExecutionRepository driftGovernanceExecutionRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDriftGovernanceExecutionRepository(jdbcTemplate);
    }

    @Bean
    DriftGovernanceReminderBatchRepository driftGovernanceReminderBatchRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDriftGovernanceReminderBatchRepository(jdbcTemplate);
    }

    @Bean
    DriftGovernanceReminderObservabilityRepository driftGovernanceReminderObservabilityRepository(
            JdbcTemplate jdbcTemplate) {
        return new JdbcDriftGovernanceReminderObservabilityRepository(jdbcTemplate);
    }

    @Bean
    RegressionPolicyRepository regressionPolicyRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegressionPolicyRepository(jdbcTemplate);
    }

    @Bean
    DeploymentRepository deploymentRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDeploymentRepository(jdbcTemplate);
    }

    @Bean
    DeploymentHealthEvaluationRepository deploymentHealthEvaluationRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDeploymentHealthEvaluationRepository(jdbcTemplate);
    }

    @Bean
    DeploymentHealthAlertRepository deploymentHealthAlertRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcDeploymentHealthAlertRepository(jdbcTemplate);
    }

    @Bean
    NotificationOutboxRepository notificationOutboxRepository(JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper, NotificationTemplateEngine templateEngine) {
        return new JdbcNotificationOutboxRepository(jdbcTemplate, objectMapper, templateEngine);
    }

    @Bean
    NotificationOperationsRepository notificationOperationsRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationOperationsRepository(jdbcTemplate);
    }

    @Bean
    NotificationOperationsGovernanceRepository notificationOperationsGovernanceRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationOperationsGovernanceRepository(jdbcTemplate);
    }

    @Bean
    NotificationAttemptArchiveRepository notificationAttemptArchiveRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationAttemptArchiveRepository(jdbcTemplate);
    }

    @Bean
    NotificationAttemptArchiveStore notificationAttemptArchiveStore(NotificationAttemptArchiveProperties properties) {
        properties.validate();
        if ("S3".equalsIgnoreCase(properties.getStorageProvider())) {
            var s3 = properties.getS3();
            var builder = S3Client.builder().region(Region.of(s3.getRegion()))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(s3.isPathStyleAccess()).build())
                    .httpClient(UrlConnectionHttpClient.builder()
                            .connectionTimeout(s3.getConnectionTimeout()).socketTimeout(s3.getSocketTimeout()).build());
            if (s3.getEndpoint() != null) builder.endpointOverride(s3.getEndpoint());
            if (s3.getAccessKey() != null && !s3.getAccessKey().isBlank()) {
                var credentials = s3.getSessionToken() == null || s3.getSessionToken().isBlank()
                        ? AwsBasicCredentials.create(s3.getAccessKey(), s3.getSecretKey())
                        : AwsSessionCredentials.create(s3.getAccessKey(), s3.getSecretKey(), s3.getSessionToken());
                builder.credentialsProvider(StaticCredentialsProvider.create(credentials));
            }
            S3Client client = builder.build();
            return new S3NotificationAttemptArchiveStore(client, s3.getBucket(), s3.getPrefix(),
                    properties.getMaximumArtifactBytes(), s3.isRequireVersioning(), s3.isRequireObjectLock(),
                    s3.getRetention(), s3.getObjectLockMode());
        }
        return new FileSystemNotificationAttemptArchiveStore(properties.getDirectory(),
                properties.getMaximumArtifactBytes());
    }

    @Bean
    NotificationRoutingAssetRepository notificationRoutingAssetRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationRoutingAssetRepository(jdbcTemplate);
    }

    @Bean
    NotificationTemplateEngine notificationTemplateEngine(ObjectMapper objectMapper) {
        return new DefaultNotificationTemplateEngine(objectMapper);
    }

    @Bean
    NotificationTemplateRepository notificationTemplateRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationTemplateRepository(jdbcTemplate);
    }

    @Bean
    CredentialRefRepository credentialRefRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcCredentialRefRepository(jdbcTemplate);
    }

    @Bean
    ProviderRepository providerRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcProviderRepository(jdbcTemplate);
    }

    @Bean
    ProviderContractRepository providerContractRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcProviderContractRepository(jdbcTemplate);
    }

    @Bean
    ProviderEndpointRepository providerEndpointRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcProviderEndpointRepository(jdbcTemplate);
    }

    @Bean
    EndpointProbeRepository endpointProbeRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcEndpointProbeRepository(jdbcTemplate);
    }
}
