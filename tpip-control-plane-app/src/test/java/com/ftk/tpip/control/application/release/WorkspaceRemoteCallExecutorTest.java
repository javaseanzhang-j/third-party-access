package com.ftk.tpip.control.application.release;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.catalog.domain.model.IdempotencyClass;
import com.ftk.tpip.control.configuration.WorkspaceRemoteCallProperties;
import com.ftk.tpip.integration.domain.model.BindingStatus;
import com.ftk.tpip.integration.domain.model.BindingVersionLifecycleStatus;
import com.ftk.tpip.integration.domain.model.IntegrationBinding;
import com.ftk.tpip.integration.domain.model.IntegrationBindingVersion;
import com.ftk.tpip.integration.domain.model.MappingAssetDirection;
import com.ftk.tpip.release.domain.model.FixtureCase;
import com.ftk.tpip.release.domain.model.FixtureExecutionMode;
import com.ftk.tpip.shared.AssetCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkspaceRemoteCallExecutorTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void isDisabledByDefault() {
        var properties = new WorkspaceRemoteCallProperties();
        var executor = executor(properties, (a, b, c, d) -> { throw new AssertionError("preview must not run"); });
        assertThrows(IllegalStateException.class,
                () -> executor.execute(binding(), version(IdempotencyClass.IDEMPOTENT), fixture(), 1));
    }

    @Test
    void rejectsNonIdempotentBindingBeforeAnyNetworkWork() {
        var properties = enabled();
        var executor = executor(properties, (a, b, c, d) -> { throw new AssertionError("preview must not run"); });
        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(binding(), version(IdempotencyClass.NON_IDEMPOTENT), fixture(), 1));
    }

    @Test
    void rejectsEndpointOutsideHostAllowlist() {
        var properties = enabled();
        var endpoint = json.createObjectNode();
        endpoint.put("environmentCode", "test"); endpoint.put("baseUrl", "https://provider.example");
        endpoint.put("totalTimeoutMs", 1000);
        DeploymentBundleManifest manifest = new DeploymentBundleManifest("candidate", "0.0.0", "customer.lookup",
                "test", "binding@1", null, null, null, List.of(), null, endpoint, List.of(),
                ">=0.1 <1.0", "a".repeat(64), NOW);
        var executor = executor(properties, (a, b, c, d) -> manifest);

        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(binding(), version(IdempotencyClass.IDEMPOTENT), fixture(), 1));
    }

    @Test
    void rejectsEndpointOutsidePortAllowlist() {
        var properties = enabled();
        var endpoint = json.createObjectNode();
        endpoint.put("environmentCode", "test"); endpoint.put("baseUrl", "http://127.0.0.1:18080");
        endpoint.put("totalTimeoutMs", 1000);
        DeploymentBundleManifest manifest = new DeploymentBundleManifest("candidate", "0.0.0", "customer.lookup",
                "test", "binding@1", null, null, null, List.of(), null, endpoint, List.of(),
                ">=0.1 <1.0", "a".repeat(64), NOW);
        var executor = executor(properties, (a, b, c, d) -> manifest);

        assertThrows(IllegalArgumentException.class,
                () -> executor.execute(binding(), version(IdempotencyClass.IDEMPOTENT), fixture(), 1));
    }

    private WorkspaceRemoteCallExecutor executor(WorkspaceRemoteCallProperties properties,
            WorkspaceRemoteCallExecutor.CandidateBundleProvider previews) {
        return new WorkspaceRemoteCallExecutor(previews, properties, json, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static WorkspaceRemoteCallProperties enabled() {
        var properties = new WorkspaceRemoteCallProperties();
        properties.setEnabled(true); properties.setAllowedHosts(List.of("127.0.0.1")); properties.setAllowedPorts(List.of(19090));
        return properties;
    }

    private static IntegrationBinding binding() {
        return new IntegrationBinding(1L, AssetCode.of("customer.lookup.binding"), "Customer Lookup", 1, 1,
                "owner", BindingStatus.ACTIVE, 0, NOW, NOW);
    }

    private static IntegrationBindingVersion version(IdempotencyClass idempotency) {
        return new IntegrationBindingVersion(2L, 1, 1, 1, 2, 3, 4, 5L, 6L, null, 7L, null,
                idempotency, "{}", "{}", "b".repeat(64), BindingVersionLifecycleStatus.PUBLISHED, NOW, NOW);
    }

    private static FixtureCase fixture() {
        return new FixtureCase(3L, 1, "remote.success", "Remote success", 10,
                FixtureExecutionMode.REMOTE_CALL, MappingAssetDirection.OUTBOUND_REQUEST, "{\"customerId\":\"C1001\"}",
                null, true, null,
                "[{\"code\":\"http-status\",\"type\":\"HTTP_STATUS\",\"expected\":200}]", NOW);
    }
}
