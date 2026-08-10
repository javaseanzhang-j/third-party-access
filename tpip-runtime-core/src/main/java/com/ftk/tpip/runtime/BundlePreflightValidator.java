package com.ftk.tpip.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.bundle.DeploymentBundleManifest;
import com.ftk.tpip.bundle.CompiledServiceRouteTarget;
import com.ftk.tpip.policy.api.PolicyStage;
import com.ftk.tpip.policy.ir.CompiledPolicyStep;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BundlePreflightValidator {
    private static final Set<String> HTTP_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private final BasicJsonSchemaValidator schemas;
    private final SecretResolver secrets;

    public BundlePreflightValidator(BasicJsonSchemaValidator schemas, SecretResolver secrets) {
        this.schemas = Objects.requireNonNull(schemas);
        this.secrets = Objects.requireNonNull(secrets);
    }

    public BundlePreflightReport validate(DeploymentBundleManifest bundle) {
        Objects.requireNonNull(bundle, "bundle must not be null");
        List<String> diagnostics = new ArrayList<>();
        schema("canonicalRequestSchema", bundle.canonicalRequestSchema(), diagnostics);
        schema("canonicalResponseSchema", bundle.canonicalResponseSchema(), diagnostics);
        schema("providerRequestSchema", bundle.providerContractSnapshot().path("requestSchema"), diagnostics);
        schema("providerResponseSchema", bundle.providerContractSnapshot().path("responseSchema"), diagnostics);
        endpoint(bundle.endpointSnapshot(), diagnostics);
        policies(bundle, diagnostics);
        if(bundle.serviceRoutePlan()!=null){
            for(CompiledServiceRouteTarget target:bundle.serviceRoutePlan().targets()){
                String prefix="routeTarget["+target.bindingVersion()+"]";
                schema(prefix+".providerRequestSchema",target.providerContractSnapshot().path("requestSchema"),diagnostics);
                schema(prefix+".providerResponseSchema",target.providerContractSnapshot().path("responseSchema"),diagnostics);
                endpoint(target.endpointSnapshot(),diagnostics);
                policies(target.policyPlan(),target.secretReferences(),diagnostics);
            }
        }
        secretAvailability(bundle.secretReferences(), diagnostics);
        if (!diagnostics.isEmpty()) throw new BundlePreflightException(diagnostics);
        return new BundlePreflightReport(List.of(
                "SCHEMA_PROFILE_COMPATIBLE", "ENDPOINT_COMPATIBLE", "POLICY_PROVIDERS_AVAILABLE",
                "SECRET_REFERENCES_AVAILABLE"));
    }

    private void schema(String name, JsonNode schema, List<String> diagnostics) {
        ContractValidationResult result = schemas.validateSchema(schema);
        result.violations().forEach(value -> diagnostics.add(name + ": " + value));
    }

    private static void endpoint(JsonNode endpoint, List<String> diagnostics) {
        try {
            String base = text(endpoint, "baseUrl");
            String path = text(endpoint, "resourcePath");
            String method = text(endpoint, "httpMethod");
            URI uri = URI.create(base.endsWith("/") || path.startsWith("/") ? base + path : base + "/" + path);
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null) {
                diagnostics.add("endpoint: only absolute HTTP(S) endpoints are supported");
            }
            if (!HTTP_METHODS.contains(method)) diagnostics.add("endpoint: unsupported HTTP method " + method);
            timeout(endpoint, "connectTimeoutMs", diagnostics);
            timeout(endpoint, "readTimeoutMs", diagnostics);
            timeout(endpoint, "totalTimeoutMs", diagnostics);
            accessPlan(endpoint,diagnostics);
        } catch (RuntimeException failure) {
            diagnostics.add("endpoint: frozen endpoint is invalid");
        }
    }

    private static void accessPlan(JsonNode endpoint,List<String> diagnostics){JsonNode plan=endpoint.path("accessParameterPlan");if(plan.isMissingNode())return;if(!plan.isObject()||!plan.path("parameters").isArray()){diagnostics.add("endpoint: invalid accessParameterPlan");return;}Set<String> locations=Set.of("PATH","QUERY","HEADER","COOKIE","BODY","SIGNATURE");Set<String> sources=Set.of("FIXED","SECRET_REF","REQUEST","SYSTEM_TIME","UUID","MAPPING_OUTPUT","POLICY_OUTPUT");for(JsonNode parameter:plan.path("parameters")){String code=parameter.path("code").asText(),location=parameter.path("location").asText(),source=parameter.path("source").asText();if(code.isBlank())diagnostics.add("accessParameter: code is missing");if(!locations.contains(location))diagnostics.add("accessParameter "+code+": unsupported location "+location);if(!sources.contains(source))diagnostics.add("accessParameter "+code+": unsupported source "+source);if("SECRET_REF".equals(source)&&parameter.path("secretReference").asText().isBlank())diagnostics.add("accessParameter "+code+": secretReference is missing");if(Set.of("REQUEST","MAPPING_OUTPUT","POLICY_OUTPUT").contains(source)&&parameter.path("sourceSelector").asText().isBlank())diagnostics.add("accessParameter "+code+": sourceSelector is missing");}}

    private void policies(DeploymentBundleManifest bundle, List<String> diagnostics) {
        policies(bundle.policyPlan(),bundle.secretReferences(),diagnostics);
    }

    private void policies(com.ftk.tpip.policy.ir.CompiledPolicyPlan plan,List<String> references,List<String> diagnostics) {
        if (plan == null) return;
        Set<String> declaredSecrets = new HashSet<>(references);
        plan.stages().forEach((stage, steps) -> steps.forEach(step -> {
            if (!supported(stage, step)) {
                diagnostics.add("policy " + step.stepId() + ": unsupported provider "
                        + step.policyType() + "@" + step.policyVersion() + " at " + stage);
            }
            if (Set.of("builtin.auth.api-key", "builtin.auth.hmac-sha256").contains(step.policyType())) {
                Object reference = step.parameters().get("secretRef");
                if (!(reference instanceof String value) || !declaredSecrets.contains(value)) {
                    diagnostics.add("policy " + step.stepId() + ": secretRef is not declared by the Bundle");
                }
            }
        }));
    }

    private static boolean supported(PolicyStage stage, CompiledPolicyStep step) {
        if (!step.policyVersion().startsWith("1.")) return false;
        return switch (step.policyType()) {
            case "builtin.transport.inject" -> stage == PolicyStage.AFTER_REQUEST_MAPPING;
            case "builtin.auth.api-key" -> stage == PolicyStage.BEFORE_TRANSPORT;
            case "builtin.auth.hmac-sha256" -> stage == PolicyStage.BEFORE_TRANSPORT;
            default -> false;
        };
    }

    private void secretAvailability(List<String> references, List<String> diagnostics) {
        for (String reference : references) {
            if (!secrets.supports(reference)) {
                diagnostics.add("secretRef " + reference + ": no compatible Secret Resolver");
                continue;
            }
            try (SecretValue ignored = secrets.resolve(reference)) {
                // Availability only. Secret contents never leave the resolver boundary.
            } catch (RuntimeException failure) {
                diagnostics.add("secretRef " + reference + ": secret is unavailable");
            }
        }
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException(field + " is missing");
        return value;
    }

    private static void timeout(JsonNode endpoint, String field, List<String> diagnostics) {
        long value = endpoint.path(field).asLong(-1);
        if (value < 1 || value > 120_000) diagnostics.add("endpoint: invalid " + field);
    }
}
