package com.ftk.tpip.runtime;

import java.util.List;

public record ContractValidationResult(List<String> violations) {
    public ContractValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public boolean valid() {
        return violations.isEmpty();
    }

    public static ContractValidationResult validResult() {
        return new ContractValidationResult(List.of());
    }
}
