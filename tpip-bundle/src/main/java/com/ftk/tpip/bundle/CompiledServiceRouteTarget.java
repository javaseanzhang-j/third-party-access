package com.ftk.tpip.bundle;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.mapping.ir.CompiledMappingPlan;
import com.ftk.tpip.policy.ir.CompiledPolicyPlan;
import com.ftk.tpip.routing.domain.model.RouteHealthRequirement;
import com.ftk.tpip.routing.domain.model.RouteManualStatus;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable execution snapshot for one route target. */
public record CompiledServiceRouteTarget(
        long bindingId,
        String bindingVersion,
        boolean enabled,
        int priority,
        int weight,
        RouteHealthRequirement healthRequirement,
        RouteManualStatus manualStatus,
        Map<String, Object> conditions,
        JsonNode providerContractSnapshot,
        List<CompiledMappingPlan> mappingPlans,
        CompiledPolicyPlan policyPlan,
        JsonNode endpointSnapshot,
        List<String> secretReferences) {

    public CompiledServiceRouteTarget {
        if (bindingId <= 0) throw new IllegalArgumentException("bindingId must be positive");
        bindingVersion = required(bindingVersion, "bindingVersion");
        if (priority < 0 || priority > 10_000) throw new IllegalArgumentException("priority is invalid");
        if (weight < 1 || weight > 10_000) throw new IllegalArgumentException("weight is invalid");
        healthRequirement = Objects.requireNonNull(healthRequirement);
        manualStatus = Objects.requireNonNull(manualStatus);
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
        providerContractSnapshot = Objects.requireNonNull(providerContractSnapshot);
        mappingPlans = List.copyOf(Objects.requireNonNull(mappingPlans));
        endpointSnapshot = Objects.requireNonNull(endpointSnapshot);
        secretReferences = secretReferences == null ? List.of() : List.copyOf(secretReferences);
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field + " must not be null").trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " must not be blank");
        return normalized;
    }
}
