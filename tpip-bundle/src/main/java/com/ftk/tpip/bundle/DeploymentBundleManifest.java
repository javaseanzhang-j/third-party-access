package com.ftk.tpip.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DeploymentBundleManifest(
        String bundleCode,
        String bundleVersion,
        String operationCode,
        String environmentCode,
        String bindingVersion,
        JsonNode canonicalRequestSchema,
        JsonNode canonicalResponseSchema,
        JsonNode providerContractSnapshot,
        List<CompiledMappingPlan> mappingPlans,
        CompiledPolicyPlan policyPlan,
        JsonNode endpointSnapshot,
        List<String> secretReferences,
        String runtimeCompatibility,
        CompiledServiceRoutePlan serviceRoutePlan,
        String checksum,
        Instant compiledAt) {

    public DeploymentBundleManifest {
        bundleCode = Objects.requireNonNull(bundleCode, "bundleCode must not be null");
        bundleVersion = Objects.requireNonNull(bundleVersion, "bundleVersion must not be null");
        operationCode = Objects.requireNonNull(operationCode, "operationCode must not be null");
        environmentCode = Objects.requireNonNull(environmentCode, "environmentCode must not be null");
        mappingPlans = mappingPlans == null ? List.of() : List.copyOf(mappingPlans);
        secretReferences = secretReferences == null ? List.of() : List.copyOf(secretReferences);
        checksum = Objects.requireNonNull(checksum, "checksum must not be null");
        compiledAt = Objects.requireNonNull(compiledAt, "compiledAt must not be null");
    }

    public DeploymentBundleManifest(String bundleCode, String bundleVersion, String operationCode,
            String environmentCode, String bindingVersion, JsonNode canonicalRequestSchema,
            JsonNode canonicalResponseSchema, JsonNode providerContractSnapshot,
            List<CompiledMappingPlan> mappingPlans, CompiledPolicyPlan policyPlan, JsonNode endpointSnapshot,
            List<String> secretReferences, String runtimeCompatibility, String checksum, Instant compiledAt) {
        this(bundleCode, bundleVersion, operationCode, environmentCode, bindingVersion, canonicalRequestSchema,
                canonicalResponseSchema, providerContractSnapshot, mappingPlans, policyPlan, endpointSnapshot,
                secretReferences, runtimeCompatibility, null, checksum, compiledAt);
    }
}
