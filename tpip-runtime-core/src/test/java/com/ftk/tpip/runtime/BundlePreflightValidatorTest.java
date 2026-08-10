package com.ftk.tpip.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.mapping.api.MappingDirection;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.policy.api.PolicyFailureAction;
import com.ftk.tpip.policy.api.PolicyStage;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyStep;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BundlePreflightValidatorTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void verifiesSchemaEndpointPolicyAndSecretAvailability() throws Exception {
        AtomicInteger resolutions = new AtomicInteger();
        SecretResolver resolver = new SecretResolver() {
            @Override public boolean supports(String reference) { return reference.startsWith("env://TPIP_SECRET_"); }
            @Override public SecretValue resolve(String reference) {
                resolutions.incrementAndGet();
                return SecretValue.of("available".toCharArray());
            }
        };
        var report = new BundlePreflightValidator(new BasicJsonSchemaValidator(), resolver).validate(bundle(
                json.readTree("{\"type\":\"object\"}"), apiKeyPolicy("builtin.auth.api-key"),
                List.of("env://TPIP_SECRET_PROVIDER_KEY")));
        assertEquals(4, report.checks().size());
        assertEquals(1, resolutions.get());
    }

    @Test
    void rejectsUnsupportedSchemaBeforeActivation() throws Exception {
        var exception = assertThrows(BundlePreflightException.class,
                () -> validator(SecretResolver.unavailable()).validate(bundle(
                        json.readTree("{\"type\":\"object\",\"oneOf\":[]}"), null, List.of())));
        assertTrue(exception.diagnostics().stream().anyMatch(value -> value.contains("oneOf")));
    }

    @Test
    void rejectsUnknownPolicyProvider() throws Exception {
        var exception = assertThrows(BundlePreflightException.class,
                () -> validator(SecretResolver.unavailable()).validate(bundle(
                        json.readTree("{\"type\":\"object\"}"), apiKeyPolicy("builtin.auth.unknown"), List.of())));
        assertTrue(exception.diagnostics().stream().anyMatch(value -> value.contains("unsupported provider")));
    }

    @Test
    void rejectsUnavailableSecretReference() throws Exception {
        var exception = assertThrows(BundlePreflightException.class,
                () -> validator(SecretResolver.unavailable()).validate(bundle(
                        json.readTree("{\"type\":\"object\"}"), apiKeyPolicy("builtin.auth.api-key"),
                        List.of("env://TPIP_SECRET_PROVIDER_KEY"))));
        assertTrue(exception.diagnostics().stream().anyMatch(value -> value.contains("no compatible Secret Resolver")));
    }

    private BundlePreflightValidator validator(SecretResolver resolver) {
        return new BundlePreflightValidator(new BasicJsonSchemaValidator(), resolver);
    }

    private CompiledPolicyPlan apiKeyPolicy(String type) {
        var step = new CompiledPolicyStep("api-key", type, "1.0.0", null,
                Map.of("secretRef", "env://TPIP_SECRET_PROVIDER_KEY"), PolicyFailureAction.FAIL, 100);
        return new CompiledPolicyPlan("auth.policy", 1,
                Map.of(PolicyStage.BEFORE_TRANSPORT, List.of(step)), "c".repeat(64));
    }

    private DeploymentBundleManifest bundle(JsonNode schema, CompiledPolicyPlan policy, List<String> secrets)
            throws Exception {
        JsonNode provider = json.readTree("""
                {"requestSchema":{"type":"object"},"responseSchema":{"type":"object"}}
                """);
        JsonNode endpoint = json.readTree("""
                {"endpointCode":"provider.customer","baseUrl":"https://provider.example","resourcePath":"/customers",
                 "httpMethod":"POST","connectTimeoutMs":1000,"readTimeoutMs":3000,"totalTimeoutMs":5000}
                """);
        return new DeploymentBundleManifest("customer.bundle", "1.0.0", "customer.lookup", "test", "binding@1",
                schema, json.readTree("{\"type\":\"object\"}"), provider,
                List.of(plan(MappingDirection.OUTBOUND_REQUEST), plan(MappingDirection.INBOUND_RESPONSE)),
                policy, endpoint, secrets, ">=0.1 <1.0", "d".repeat(64), Instant.parse("2026-08-08T00:00:00Z"));
    }

    private static CompiledMappingPlan plan(MappingDirection direction) {
        return new CompiledMappingPlan(direction.name().toLowerCase(), 1, direction, List.of(), "a".repeat(64));
    }
}
