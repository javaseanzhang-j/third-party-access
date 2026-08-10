package com.ftk.tpip.policy.ir;

import com.ftk.tpip.policy.api.PolicyStage;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record CompiledPolicyPlan(
        String policyCode,
        int version,
        Map<PolicyStage, List<CompiledPolicyStep>> stages,
        String checksum) {

    public CompiledPolicyPlan {
        policyCode = Objects.requireNonNull(policyCode, "policyCode must not be null");
        stages = Map.copyOf(Objects.requireNonNull(stages, "stages must not be null"));
        checksum = Objects.requireNonNull(checksum, "checksum must not be null");
    }
}
