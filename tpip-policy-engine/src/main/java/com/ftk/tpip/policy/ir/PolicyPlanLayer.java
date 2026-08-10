package com.ftk.tpip.policy.ir;

import java.util.Objects;
import java.util.Set;

/** One ordered policy source, such as channel defaults, interface overrides, or implementation rules. */
public record PolicyPlanLayer(String layerCode, CompiledPolicyPlan plan, Set<String> disabledStepIds) {
    public PolicyPlanLayer {
        layerCode = Objects.requireNonNull(layerCode, "layerCode must not be null");
        disabledStepIds = disabledStepIds == null ? Set.of() : Set.copyOf(disabledStepIds);
        if (plan == null && disabledStepIds.isEmpty()) throw new IllegalArgumentException("policy layer must change the effective plan");
    }
}
