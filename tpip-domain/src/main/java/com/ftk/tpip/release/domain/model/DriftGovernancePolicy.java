package com.ftk.tpip.release.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record DriftGovernancePolicy(Long id, AssetCode policyCode, String policyName,
        DriftGovernancePolicyScope scope, Long workspaceId, DriftGovernancePolicyStatus status,
        Long currentVersionId, long rowVersion, Instant createdAt, Instant updatedAt) {
    public DriftGovernancePolicy {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        Objects.requireNonNull(policyCode, "policyCode must not be null");
        if (policyName == null || policyName.isBlank() || policyName.trim().length() > 200)
            throw new IllegalArgumentException("policyName is invalid");
        policyName = policyName.trim();
        Objects.requireNonNull(scope, "scope must not be null");
        if ((scope == DriftGovernancePolicyScope.GLOBAL) != (workspaceId == null))
            throw new IllegalArgumentException("workspaceId must match policy scope");
        if (workspaceId != null && workspaceId <= 0) throw new IllegalArgumentException("workspaceId must be positive");
        Objects.requireNonNull(status, "status must not be null");
        if (currentVersionId != null && currentVersionId <= 0)
            throw new IllegalArgumentException("currentVersionId must be positive");
        if (status == DriftGovernancePolicyStatus.ACTIVE && currentVersionId == null)
            throw new IllegalArgumentException("ACTIVE policy requires a published version");
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }
}
