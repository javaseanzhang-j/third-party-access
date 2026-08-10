package com.ftk.tpip.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import java.util.*;

public record BundleCompilationRequest(
        String bundleCode, String bundleVersion, String operationCode, String environmentCode,
        String bindingVersion, JsonNode canonicalRequestSchema, JsonNode canonicalResponseSchema,
        JsonNode providerContractSnapshot, List<CompiledMappingPlan> mappingPlans,
        CompiledPolicyPlan policyPlan, JsonNode endpointSnapshot, List<String> secretReferences,
        String runtimeCompatibility, CompiledServiceRoutePlan serviceRoutePlan) {
    public BundleCompilationRequest {
        bundleCode=required(bundleCode,"bundleCode"); bundleVersion=required(bundleVersion,"bundleVersion");
        operationCode=required(operationCode,"operationCode"); environmentCode=required(environmentCode,"environmentCode");
        bindingVersion=required(bindingVersion,"bindingVersion");
        canonicalRequestSchema=Objects.requireNonNull(canonicalRequestSchema,"canonicalRequestSchema must not be null");
        canonicalResponseSchema=Objects.requireNonNull(canonicalResponseSchema,"canonicalResponseSchema must not be null");
        providerContractSnapshot=Objects.requireNonNull(providerContractSnapshot,"providerContractSnapshot must not be null");
        endpointSnapshot=Objects.requireNonNull(endpointSnapshot,"endpointSnapshot must not be null");
        mappingPlans=List.copyOf(Objects.requireNonNull(mappingPlans,"mappingPlans must not be null"));
        secretReferences=secretReferences==null?List.of():List.copyOf(secretReferences);
        runtimeCompatibility=required(runtimeCompatibility,"runtimeCompatibility");
    }
    public BundleCompilationRequest(String bundleCode, String bundleVersion, String operationCode,
            String environmentCode, String bindingVersion, JsonNode canonicalRequestSchema,
            JsonNode canonicalResponseSchema, JsonNode providerContractSnapshot,
            List<CompiledMappingPlan> mappingPlans, CompiledPolicyPlan policyPlan, JsonNode endpointSnapshot,
            List<String> secretReferences, String runtimeCompatibility) {
        this(bundleCode, bundleVersion, operationCode, environmentCode, bindingVersion,
                canonicalRequestSchema, canonicalResponseSchema, providerContractSnapshot, mappingPlans,
                policyPlan, endpointSnapshot, secretReferences, runtimeCompatibility, null);
    }
    private static String required(String value,String field){String v=Objects.requireNonNull(value,field+" must not be null").trim();if(v.isEmpty())throw new IllegalArgumentException(field+" must not be blank");return v;}
}
