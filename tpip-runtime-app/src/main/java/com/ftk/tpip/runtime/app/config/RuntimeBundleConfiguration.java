package com.ftk.tpip.runtime.app.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.runtime.BundleManifestDecoder;
import com.ftk.tpip.runtime.BundlePreflightValidator;
import com.ftk.tpip.runtime.BasicJsonSchemaValidator;
import com.ftk.tpip.runtime.DefaultRuntimePipeline;
import com.ftk.tpip.runtime.DefaultBundleResolver;
import com.ftk.tpip.runtime.DefaultDeploymentResolver;
import com.ftk.tpip.runtime.RuntimePipeline;
import com.ftk.tpip.runtime.RuntimePolicyExecutor;
import com.ftk.tpip.runtime.SecretResolver;
import com.ftk.tpip.runtime.RuntimeVersion;
import com.ftk.tpip.runtime.InMemoryRuntimeRouteHealthRegistry;
import com.ftk.tpip.runtime.RuntimeRouteHealthRegistry;
import com.ftk.tpip.runtime.RuntimeAccessParameterAssembler;
import com.ftk.tpip.mapping.execution.DefaultMappingEngine;
import com.ftk.tpip.runtime.app.infrastructure.HttpBundleArtifactSource;
import com.ftk.tpip.runtime.app.infrastructure.HttpDeploymentRouteSource;
import com.ftk.tpip.adapters.runtime.JdkHttpProviderTransport;
import com.ftk.tpip.adapters.runtime.EnvironmentSecretResolver;
import com.ftk.tpip.runtime.app.observability.MicrometerRuntimeInvocationObserver;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RuntimeBundleProperties.class)
public class RuntimeBundleConfiguration {
    @Bean
    ConfiguredBundleReferenceRegistry bundleReferenceRegistry(RuntimeBundleProperties properties) {
        return new ConfiguredBundleReferenceRegistry(properties.getBundles());
    }

    @Bean
    HttpBundleArtifactSource bundleArtifactSource(RuntimeBundleProperties properties) {
        return new HttpBundleArtifactSource(properties.getControlPlaneBaseUri(), properties.getConnectTimeout(),
                properties.getReadTimeout(), properties.getMaxArtifactBytes());
    }

    @Bean
    DefaultBundleResolver defaultBundleResolver(ConfiguredBundleReferenceRegistry references,
            HttpBundleArtifactSource artifacts, RuntimeBundleProperties properties, ObjectMapper json) {
        BundleManifestDecoder decoder = content -> {
            try {
                return json.readValue(content, DeploymentBundleManifest.class);
            } catch (Exception exception) {
                throw new IllegalArgumentException("Invalid Bundle Manifest JSON", exception);
            }
        };
        return new DefaultBundleResolver(references, artifacts, decoder, json,
                RuntimeVersion.parse(properties.getRuntimeVersion()), properties.getCacheTtl(),
                properties.getMaxStale(), properties.getMaxArtifactBytes(), Clock.systemUTC());
    }

    @Bean
    HttpDeploymentRouteSource deploymentRouteSource(RuntimeBundleProperties properties, ObjectMapper json) {
        return new HttpDeploymentRouteSource(properties.getControlPlaneBaseUri(), properties.getConnectTimeout(),
                properties.getReadTimeout(), json);
    }

    @Bean
    DefaultDeploymentResolver deploymentResolver(HttpDeploymentRouteSource routes,
            DefaultBundleResolver bundles, RuntimeBundleProperties properties) {
        return new DefaultDeploymentResolver(routes, bundles, properties.getRouteTtl(),
                properties.getRouteMaxStale(), Clock.systemUTC());
    }

    @Bean
    SecretResolver secretResolver() {
        return new EnvironmentSecretResolver();
    }

    @Bean
    BasicJsonSchemaValidator runtimeSchemaValidator() {
        return new BasicJsonSchemaValidator();
    }

    @Bean
    RuntimePolicyExecutor runtimePolicyExecutor(SecretResolver secrets) {
        return new RuntimePolicyExecutor(secrets);
    }

    @Bean
    RuntimeRouteHealthRegistry runtimeRouteHealthRegistry() {
        return new InMemoryRuntimeRouteHealthRegistry(3);
    }

    @Bean RuntimeAccessParameterAssembler runtimeAccessParameterAssembler(ObjectMapper json,SecretResolver secrets){
        return new RuntimeAccessParameterAssembler(json,secrets,Clock.systemUTC());
    }

    @Bean
    BundlePreflightValidator bundlePreflightValidator(BasicJsonSchemaValidator schemas, SecretResolver secrets) {
        return new BundlePreflightValidator(schemas, secrets);
    }

    @Bean
    MicrometerRuntimeInvocationObserver runtimeInvocationObserver(MeterRegistry registry, ObjectMapper json,
            RuntimeBundleProperties properties) {
        return new MicrometerRuntimeInvocationObserver(registry, json, properties.getInstanceId(),
                properties.isAuditEnabled());
    }

    @Bean
    RuntimePipeline runtimePipeline(DefaultDeploymentResolver deployments, ObjectMapper json,
            RuntimeBundleProperties properties, BasicJsonSchemaValidator schemas,
            RuntimePolicyExecutor policies, MicrometerRuntimeInvocationObserver observer,
            RuntimeRouteHealthRegistry routeHealth,RuntimeAccessParameterAssembler accessParameters) {
        return new DefaultRuntimePipeline(deployments, new DefaultMappingEngine(json),
                schemas, policies,
                new JdkHttpProviderTransport(json, properties.getMaxProviderResponseBytes()),
                properties.getEnvironmentCode(), Clock.systemUTC(), observer, System::nanoTime, routeHealth,accessParameters);
    }
}
