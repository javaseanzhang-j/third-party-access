package com.ftk.tpip.policy.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ftk.tpip.shared.*;
import java.util.*;

public record PolicyTypeDescriptor(AssetCode policyTypeCode, SemanticVersion semanticVersion,
        Set<PolicyStage> allowedStages, JsonNode configurationSchema, String runtimeCompatibility,
        boolean deterministic, boolean sideEffect, String securityClassification,
        String idempotencyRequirement, boolean active) {
    public PolicyTypeDescriptor {
        policyTypeCode = Objects.requireNonNull(policyTypeCode); semanticVersion = Objects.requireNonNull(semanticVersion);
        allowedStages = Set.copyOf(Objects.requireNonNull(allowedStages));
        if (allowedStages.isEmpty()) throw new IllegalArgumentException("allowedStages must not be empty");
        configurationSchema = Objects.requireNonNull(configurationSchema).deepCopy();
        if (!configurationSchema.isObject()) throw new IllegalArgumentException("configurationSchema must be an object");
        runtimeCompatibility = required(runtimeCompatibility, "runtimeCompatibility");
        securityClassification = required(securityClassification, "securityClassification");
    }
    public String reference() { return policyTypeCode.value() + "@" + semanticVersion; }
    private static String required(String value, String field) {
        String result = Objects.requireNonNull(value, field).trim();
        if (result.isEmpty()) throw new IllegalArgumentException(field + " must not be blank"); return result;
    }
}
