package com.ftk.tpip.control.application.release;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.adapters.runtime.EnvironmentSecretResolver;
import com.ftk.tpip.adapters.runtime.JdkHttpProviderTransport;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import com.ftk.tpip.contract.InvocationMetadata;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.contract.InvocationResponse;
import com.ftk.tpip.control.application.integration.BindingVersionBundlePreviewApplicationService;
import com.ftk.tpip.control.configuration.WorkspaceRemoteCallProperties;
import com.ftk.tpip.integration.domain.model.IntegrationBinding;
import com.ftk.tpip.integration.domain.model.IntegrationBindingVersion;
import com.ftk.tpip.mapping.execution.DefaultMappingEngine;
import com.ftk.tpip.release.domain.model.FixtureCase;
import com.ftk.tpip.runtime.BasicJsonSchemaValidator;
import com.ftk.tpip.runtime.BundlePreflightValidator;
import com.ftk.tpip.runtime.DefaultRuntimePipeline;
import com.ftk.tpip.runtime.ProviderTransport;
import com.ftk.tpip.runtime.ProviderTransportRequest;
import com.ftk.tpip.runtime.ProviderTransportResponse;
import com.ftk.tpip.runtime.ResolvedDeployment;
import com.ftk.tpip.runtime.RuntimePolicyExecutor;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Executes an immutable candidate Bundle through the runtime pipeline under explicit verification safety gates. */
@Component
public final class WorkspaceRemoteCallExecutor {
    private final CandidateBundleProvider previews;
    private final WorkspaceRemoteCallProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public WorkspaceRemoteCallExecutor(BindingVersionBundlePreviewApplicationService previews,
            WorkspaceRemoteCallProperties properties, ObjectMapper json) {
        this(previews::preview, properties, json, Clock.systemUTC());
    }

    WorkspaceRemoteCallExecutor(CandidateBundleProvider previews,
            WorkspaceRemoteCallProperties properties, ObjectMapper json, Clock clock) {
        this.previews = previews; this.properties = properties; this.json = json; this.clock = clock;
        properties.validate();
    }

    public RemoteCallResult execute(IntegrationBinding binding, IntegrationBindingVersion version,
            FixtureCase fixture, long runId) {
        if (!properties.isEnabled()) throw new IllegalStateException("REMOTE_CALL verification is disabled");
        if (version.idempotencyClass() != IdempotencyClass.IDEMPOTENT) {
            throw new IllegalArgumentException("REMOTE_CALL requires an IDEMPOTENT BindingVersion");
        }
        JsonNode source = read(fixture.sourceDocument());
        int requestBytes = bytes(source);
        if (requestBytes > properties.getMaximumRequestBytes()) {
            throw new IllegalArgumentException("REMOTE_CALL request exceeds maximumRequestBytes");
        }
        DeploymentBundleManifest manifest = previews.preview(binding.id(), version.id(),
                "verification.remote." + runId, "0.0.0");
        validateEndpoint(manifest.endpointSnapshot());

        EnvironmentSecretResolver secrets = new EnvironmentSecretResolver();
        new BundlePreflightValidator(new BasicJsonSchemaValidator(), secrets).validate(manifest);
        AtomicReference<ProviderTransportResponse> captured = new AtomicReference<>();
        ProviderTransport delegate = new JdkHttpProviderTransport(json, properties.getMaximumResponseBytes());
        ProviderTransport transport = request -> {
            ProviderTransportResponse response = delegate.exchange(request);
            captured.set(response);
            return response;
        };
        var deployment = new ResolvedDeployment(0, "workspace-verification", "candidate", BigDecimal.valueOf(100), manifest);
        var pipeline = new DefaultRuntimePipeline((operation, environment, routingKey) -> deployment,
                new DefaultMappingEngine(json), new BasicJsonSchemaValidator(), new RuntimePolicyExecutor(secrets),
                transport, manifest.environmentCode(), clock);
        String requestId = "verification-" + runId + "-" + fixture.caseCode();
        InvocationRequest request = new InvocationRequest(new InvocationMetadata(requestId,
                "tpip-workspace-verification", null, null, clock.instant().plus(properties.getMaximumTotalTimeout()),
                Map.of("verificationRunId", Long.toString(runId))), source);
        InvocationResponse response = pipeline.invoke(manifest.operationCode(), request);
        return new RemoteCallResult(response, captured.get(), requestBytes);
    }

    public void validateFixtureCount(long count) {
        if (count > properties.getMaximumCallsPerRun()) {
            throw new IllegalArgumentException("REMOTE_CALL fixture count exceeds maximumCallsPerRun");
        }
    }

    private void validateEndpoint(JsonNode endpoint) {
        String environment = endpoint.path("environmentCode").asText().toLowerCase(Locale.ROOT);
        if (!properties.getAllowedEnvironments().contains(environment)) {
            throw new IllegalArgumentException("REMOTE_CALL endpoint environment is not allowlisted: " + environment);
        }
        URI base = URI.create(endpoint.path("baseUrl").asText());
        String host = base.getHost() == null ? "" : base.getHost().toLowerCase(Locale.ROOT);
        if (!properties.getAllowedHosts().contains(host)) {
            throw new IllegalArgumentException("REMOTE_CALL endpoint host is not allowlisted: " + host);
        }
        int port = base.getPort() > 0 ? base.getPort() : "https".equalsIgnoreCase(base.getScheme()) ? 443 : 80;
        if (!properties.getAllowedPorts().contains(port)) {
            throw new IllegalArgumentException("REMOTE_CALL endpoint port is not allowlisted: " + port);
        }
        if ("http".equalsIgnoreCase(base.getScheme()) && !isLoopback(host)) {
            throw new IllegalArgumentException("REMOTE_CALL requires HTTPS for non-loopback endpoints");
        }
        long timeout = endpoint.path("totalTimeoutMs").asLong(-1);
        if (timeout < 1 || timeout > properties.getMaximumTotalTimeout().toMillis()) {
            throw new IllegalArgumentException("REMOTE_CALL endpoint timeout exceeds the verification limit");
        }
    }

    private int bytes(JsonNode value) {
        try {
            return json.writeValueAsBytes(value).length;
        } catch (Exception failure) {
            throw new IllegalArgumentException("REMOTE_CALL source cannot be serialized", failure);
        }
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (Exception failure) {
            throw new IllegalStateException("Stored REMOTE_CALL source is invalid", failure);
        }
    }

    private static boolean isLoopback(String host) {
        return Objects.equals(host, "localhost") || Objects.equals(host, "127.0.0.1") || Objects.equals(host, "::1");
    }

    public record RemoteCallResult(InvocationResponse invocation, ProviderTransportResponse provider, int requestBytes) {}

    @FunctionalInterface
    interface CandidateBundleProvider {
        DeploymentBundleManifest preview(long bindingId, long versionId, String bundleCode, String bundleVersion);
    }
}
