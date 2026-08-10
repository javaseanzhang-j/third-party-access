package com.ftk.tpip.policy.ir;

import java.util.Map;
import com.ftk.tpip.policy.api.PolicyFailureAction;

public record CompiledPolicyStep(
        String stepId,
        String policyType,
        String policyVersion,
        String conditionExpression,
        Map<String, Object> parameters,
        PolicyFailureAction onFailure,
        Integer timeoutMs) {

    public CompiledPolicyStep {
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }
}
