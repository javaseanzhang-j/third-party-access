package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.bundle.CompiledServiceRoutePlan;
import com.ftk.tpip.bundle.CompiledServiceRouteTarget;
import com.ftk.tpip.contract.InvocationMetadata;
import com.ftk.tpip.contract.InvocationRequest;
import com.ftk.tpip.mapping.api.ArrayStrategy;
import com.ftk.tpip.mapping.api.ErrorStrategy;
import com.ftk.tpip.mapping.api.MappingDirection;
import com.ftk.tpip.mapping.api.MissingStrategy;
import com.ftk.tpip.mapping.api.ValueSource;
import com.ftk.tpip.mapping.execution.DefaultMappingEngine;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.mapping.ir.CompiledMappingRule;
import com.ftk.tpip.policy.api.PolicyFailureAction;
import com.ftk.tpip.policy.api.PolicyStage;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyStep;
import com.ftk.tpip.routing.domain.model.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class DefaultRuntimePipelineTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void executesValidationMappingPolicyTransportAndResponseMapping() throws Exception {
        AtomicReference<ProviderTransportRequest> exchanged = new AtomicReference<>();
        ProviderTransport transport = request -> {
            exchanged.set(request);
            return new ProviderTransportResponse(200, Map.of(), json.createObjectNode().put("result_name", "Sean"),
                    Duration.ofMillis(12));
        };
        var response = pipeline(manifest(), transport).invoke("customer.lookup", request("{\"customerId\":\"C-1\"}"));

        assertEquals(true, response.result().success());
        assertEquals("Sean", response.payload().path("name").asText());
        assertEquals("C-1", exchanged.get().body().path("user_id").asText());
        assertEquals(List.of("req-1"), exchanged.get().headers().get("X-Request-Id"));
        assertEquals("1.0.0", response.meta().bundleVersion());
    }

    @Test
    void rejectsInvalidCanonicalRequestBeforeTransport() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var response = pipeline(manifest(), request -> {
            calls.incrementAndGet();
            throw new AssertionError("transport must not be called");
        }).invoke("customer.lookup", request("{}"));

        assertEquals(false, response.result().success());
        assertEquals("CANONICAL_REQUEST_INVALID", response.result().code());
        assertEquals(0, calls.get());
        assertNull(response.payload());
    }

    @Test
    void normalizesProviderHttpFailure() throws Exception {
        var response = pipeline(manifest(), request -> new ProviderTransportResponse(503, Map.of(),
                json.createObjectNode().put("error", "busy"), Duration.ofMillis(3)))
                .invoke("customer.lookup", request("{\"customerId\":\"C-1\"}"));
        assertEquals(false, response.result().success());
        assertEquals("PROVIDER_HTTP_ERROR", response.result().code());
    }

    @Test
    void executesTheTargetFrozenInThePublishedServiceRoute() throws Exception {
        AtomicReference<ProviderTransportRequest> exchanged=new AtomicReference<>();
        var response=pipeline(routedManifest(),request->{exchanged.set(request);return new ProviderTransportResponse(200,
                Map.of(),json.createObjectNode().put("result_name","Routed"),Duration.ofMillis(1));})
                .invoke("customer.lookup",request("{\"customerId\":\"C-1\"}"));
        assertEquals(true,response.result().success());
        assertEquals("route-provider.example",exchanged.get().uri().getHost());
        assertEquals("provider.route",response.meta().providerCode());
    }

    @Test
    void emitsSafeStageLevelObservation() throws Exception {
        AtomicReference<RuntimeInvocationObservation> observed = new AtomicReference<>();
        AtomicLong ticker = new AtomicLong();
        DeploymentBundleManifest manifest = manifest();
        DeploymentResolver deployments = (operation, environment, routingKey) -> new ResolvedDeployment(
                1, "customer.lookup.prod", "r1", new BigDecimal("100"), manifest);
        ProviderTransport transport = request -> new ProviderTransportResponse(200, Map.of(),
                json.createObjectNode().put("result_name", "Sean"), Duration.ofMillis(12));
        var pipeline = new DefaultRuntimePipeline(deployments, new DefaultMappingEngine(json),
                new BasicJsonSchemaValidator(), new RuntimePolicyExecutor(), transport, "test", clock,
                observed::set, () -> ticker.getAndAdd(1_000_000));

        pipeline.invoke("customer.lookup", request("{\"customerId\":\"C-1\"}"));

        assertEquals("SUCCESS", observed.get().resultCode());
        assertEquals("customer.lookup.prod", observed.get().deploymentCode());
        assertEquals(200, observed.get().providerStatusCode());
        assertEquals(13, observed.get().stages().size());
        assertEquals(RuntimeStage.TRANSPORT, observed.get().stages().stream()
                .filter(stage -> stage.stage() == RuntimeStage.TRANSPORT).findFirst().orElseThrow().stage());
    }

    @Test
    void observesBundleResolutionFailureWithoutPayload() throws Exception {
        AtomicReference<RuntimeInvocationObservation> observed = new AtomicReference<>();
        var pipeline = new DefaultRuntimePipeline((operation, environment, routingKey) -> {
            throw new BundleResolutionException(BundleResolutionCode.SOURCE_UNAVAILABLE, "control unavailable");
        }, new DefaultMappingEngine(json), new BasicJsonSchemaValidator(), new RuntimePolicyExecutor(),
                request -> { throw new AssertionError("transport must not be called"); }, "test", clock,
                observed::set, new AtomicLong()::getAndIncrement);
        assertThrows(BundleResolutionException.class,
                () -> pipeline.invoke("customer.lookup", request("{\"customerId\":\"C-1\"}")));
        assertEquals("BUNDLE_SOURCE_UNAVAILABLE", observed.get().resultCode());
        assertEquals("unresolved", observed.get().deploymentCode());
        assertEquals(0, observed.get().stages().size());
    }

    private DefaultRuntimePipeline pipeline(DeploymentBundleManifest manifest, ProviderTransport transport) {
        DeploymentResolver deployments = (operation, environment, routingKey) -> new ResolvedDeployment(
                1, "customer.lookup.prod", "r1", new BigDecimal("100"), manifest);
        return new DefaultRuntimePipeline(deployments, new DefaultMappingEngine(json),
                new BasicJsonSchemaValidator(), new RuntimePolicyExecutor(), transport, "test", clock);
    }

    private InvocationRequest request(String payload) throws Exception {
        return new InvocationRequest(new InvocationMetadata("req-1", "test-suite", null, null, null,
                Map.of("traceId", "trace-1")), json.readTree(payload));
    }

    private DeploymentBundleManifest manifest() throws Exception {
        var requestSchema = json.readTree("""
                {"type":"object","required":["customerId"],"properties":{"customerId":{"type":"string"}}}
                """);
        var responseSchema = json.readTree("""
                {"type":"object","required":["name"],"properties":{"name":{"type":"string"}}}
                """);
        var provider = json.readTree("""
                {"requestSchema":{"type":"object","required":["user_id"],"properties":{"user_id":{"type":"string"}}},
                 "responseSchema":{"type":"object","required":["result_name"],"properties":{"result_name":{"type":"string"}}}}
                """);
        var endpoint = json.readTree("""
                {"endpointCode":"provider.customer","baseUrl":"https://provider.example","resourcePath":"/customers",
                 "httpMethod":"POST","connectTimeoutMs":1000,"readTimeoutMs":3000,"totalTimeoutMs":5000}
                """);
        var inject = new CompiledPolicyStep("request-id", "builtin.transport.inject", "1.0.0", null,
                Map.of("headers", Map.of("X-Request-Id", "${context.requestId}")), PolicyFailureAction.FAIL, 100);
        var policy = new CompiledPolicyPlan("customer.policy", 1,
                Map.of(PolicyStage.AFTER_REQUEST_MAPPING, List.of(inject)), "c".repeat(64));
        return new DeploymentBundleManifest("customer.bundle", "1.0.0", "customer.lookup", "test", "binding@1",
                requestSchema, responseSchema, provider, List.of(
                        plan(MappingDirection.OUTBOUND_REQUEST, "$.customerId", "$.user_id"),
                        plan(MappingDirection.INBOUND_RESPONSE, "$.result_name", "$.name")),
                policy, endpoint, List.of(), ">=0.1 <1.0", "d".repeat(64), clock.instant());
    }

    private DeploymentBundleManifest routedManifest() throws Exception {
        DeploymentBundleManifest root=manifest();
        var targetEndpoint=json.readTree("""
                {"endpointCode":"provider.route","baseUrl":"https://route-provider.example","resourcePath":"/customers",
                 "httpMethod":"POST","connectTimeoutMs":1000,"readTimeoutMs":3000,"totalTimeoutMs":5000}
                """);
        var target=new CompiledServiceRouteTarget(42,"customer.route@3",true,0,100,
                RouteHealthRequirement.HEALTHY_OR_UNKNOWN,RouteManualStatus.AVAILABLE,Map.of(),root.providerContractSnapshot(),
                root.mappingPlans(),root.policyPlan(),targetEndpoint,List.of());
        var route=new CompiledServiceRoutePlan(7,8,2,true,RouteFallbackMode.ONLY_NOT_SENT,
                "a".repeat(64),List.of(target));
        return new DeploymentBundleManifest(root.bundleCode(),root.bundleVersion(),root.operationCode(),
                root.environmentCode(),root.bindingVersion(),root.canonicalRequestSchema(),root.canonicalResponseSchema(),
                root.providerContractSnapshot(),root.mappingPlans(),root.policyPlan(),root.endpointSnapshot(),
                root.secretReferences(),root.runtimeCompatibility(),route,root.checksum(),root.compiledAt());
    }

    private static CompiledMappingPlan plan(MappingDirection direction, String source, String target) {
        var rule = new CompiledMappingRule("copy", 1, ValueSource.SELECTOR, source, target, "STRING",
                null, null, true, null, null, ArrayStrategy.ALL, MissingStrategy.FAIL, ErrorStrategy.FAIL);
        return new CompiledMappingPlan(direction.name().toLowerCase(), 1, direction, List.of(rule), "a".repeat(64));
    }
}
