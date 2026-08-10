package com.ftk.tpip.routing.domain.model;

import com.ftk.tpip.shared.AssetCode;
import java.time.Instant;
import java.util.Objects;

public record ServiceRoutePolicy(Long id, long operationId, AssetCode policyCode, String policyName,
        boolean active, long rowVersion, Instant createdAt, Instant updatedAt) {
    public ServiceRoutePolicy {
        if (id != null && id <= 0) throw new IllegalArgumentException("id must be positive");
        if (operationId <= 0) throw new IllegalArgumentException("operationId must be positive");
        policyCode = Objects.requireNonNull(policyCode);
        if (policyName == null || policyName.isBlank() || policyName.trim().length() > 200) throw new IllegalArgumentException("policyName is blank or too long");
        policyName = policyName.trim();
        if (rowVersion < 0) throw new IllegalArgumentException("rowVersion must not be negative");
    }
    public static ServiceRoutePolicy create(long operationId, AssetCode code, String name) {
        return new ServiceRoutePolicy(null, operationId, code, name, true, 0, null, null);
    }
}
